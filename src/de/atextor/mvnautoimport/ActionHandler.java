package de.atextor.mvnautoimport;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.net.proxy.IProxyData;
import org.eclipse.core.net.proxy.IProxyService;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorActionDelegate;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.statushandlers.StatusManager;

public class ActionHandler extends AbstractHandler implements IEditorActionDelegate {
  /**
   * Removes all characters from a string except a-z and A-Z 
   */
  protected String escape(final String str) {
    return str.replaceAll("[^a-zA-Z]", "");
  }
  
  /**
   * Create the lib folder in the project, if necessary
   */
  protected IFolder createLibFolder(IProject project) throws CoreException {
    final IPreferenceStore prefs = Activator.getDefault().getPreferenceStore();
    final String libFolderSetting = prefs.getString(PreferenceInitializer.P_LIBFOLDER);
    
    final IFolder lib = project.getFolder(libFolderSetting);
    if (!lib.exists()) {
        lib.create(true, true, null);
    }
    return lib;
  }

  /**
   * Runs when the action is executed and calls everything else
   */
  @Override
  public void run(IAction action) {
    // Find out the active editor, and the selection in it
    final IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
    final ISelection selection = page.getSelection();
    
    final IEditorInput input = page.getActiveEditor().getEditorInput();
    if (!(input instanceof IFileEditorInput)) {
      log(Status.ERROR, "Could not locate file in project");
      return;
    }
    
    // If no text selection or empty selection, return
    if (!(selection instanceof ITextSelection)) {
      return;
    }
    final String selectedText = ((ITextSelection)selection).getText();
    if ("".equals(selectedText)) {
      return;
    }
    
    // When we have a selection, create the lib folder if necessary
    final IFolder libFolder;
    try {
	    final IProject project = ((IFileEditorInput)input).getFile().getProject();
      libFolder = createLibFolder(project);
    } catch (CoreException e) {
      log(Status.ERROR, "Lib folder does not exist, nor could it be created", e);
      return;
    }
    
    // Load the list of matching maven artifacts that contain a class with a name similar
    // to the selected text
    final List<String> nodes = new ArrayList<String>();
    final Job job = new Job("Fetching Maven artifacts list") {
      @Override
      protected IStatus run(IProgressMonitor monitor) {
        monitor.beginTask("Fetching Maven artifacts list", 100);
        monitor.worked(IProgressMonitor.UNKNOWN);
        
        final IPreferenceStore prefs = Activator.getDefault().getPreferenceStore();
        final String mavenRepo = prefs.getString(PreferenceInitializer.P_MAVEN_REPO);
        final String xpath = prefs.getString(PreferenceInitializer.P_XPATH);
        
        // Load XML artifact list
        final BufferedReader in;
        try {
          final InputStream stream = readWebpage(new URI(mavenRepo.replace("$REPO$", escape(selectedText))));
          in = new BufferedReader(new InputStreamReader(stream));
        } catch (IOException e) {
          log(Status.ERROR, "Could not read HTTP stream", e);
          monitor.done();
          return Status.CANCEL_STATUS;
        } catch (URISyntaxException e) {
          log(Status.ERROR, "Invalid URI", e);
          monitor.done();
          return Status.CANCEL_STATUS;
        }
        
        // Parse XML
        final SAXReader reader = new SAXReader();
        final Document document;
        try {
          document = reader.read(in);
        } catch (DocumentException e) {
          log(Status.ERROR, "Could not parse XML", e);
          monitor.done();
          return Status.CANCEL_STATUS;
        }
        
        // Select the artifacts from XML. Each artifact is in the form
        // groupId:artifactId:version
        for (Object o: document.selectNodes(xpath)) {
          final Node n = (Node)o;
          final String text = n.getText();
          if (text.contains(":")) {
	          nodes.add(n.getText());
          }
        }
        
        if (nodes.size() == 0) {
          log(Status.INFO, "No artifacts found for class: " + selectedText);
          monitor.done();
          return Status.CANCEL_STATUS;
        }
      
        monitor.done();
        return Status.OK_STATUS;
      }
    };
    job.setPriority(Job.SHORT);
    // Job change listener: When the list was loaded, continue in the main thread
    job.addJobChangeListener(new JobChangeAdapter() {
      public void done(IJobChangeEvent event) {
        if (event.getResult().isOK()) {
          // As this is a job, we are not in the main thread and can not make UI changes.
          // In order to display the selection dialog, we need to put the runnable in the
          // main thread using syncExec.
          Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
              // Show selection dialog with possible artifacts
		          final String artifactString = showSelectionDialog(nodes.toArray());
		          if (artifactString == null) {
		            return;
		          }
		          // If an artifact was selected, download the file and add it to the build path
		          addJarToLibFolder(artifactString, libFolder);
            } 
          });
        }
      }
    });
    job.schedule();
  }
  
  /**
   * Reads a web resource while respecting proxy settings
   */
  protected InputStream readWebpage(URI uri) throws IOException {
    final IProxyService proxyService = Activator.getDefault().getProxyService();
    final IProxyData[] proxyDataForHost = proxyService.select(uri);
    
    for (IProxyData data : proxyDataForHost) {
        if (data.getHost() != null) {
            System.setProperty("http.proxySet", "true");
            System.setProperty("http.proxyHost", data.getHost());
        }
        if (data.getHost() != null) {
            System.setProperty("http.proxyPort", String.valueOf(data.getPort()));
        }
    }
    return uri.toURL().openStream();
	}

  /**
   * Puts message into error log
   */
  protected static void log(int severity, String message) {
    StatusManager.getManager().handle(new Status(severity, ActionHandler.class.getName(), message));
  }
  
  /**
   * Puts message into error log
   */
  protected static void log(int severity, String message, Exception e) {
    StatusManager.getManager().handle(new Status(severity, ActionHandler.class.getName(), message, e));
  }
  
  /**
   * Display the selection dialog with the list of artifacts
   */
  protected String showSelectionDialog(Object[] options) {
    final Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
    final ElementListSelectionDialog dialog = new ElementListSelectionDialog(shell, new LabelProvider());
    dialog.setTitle("Select artifact for class");
    dialog.setMessage("Select an artifact (* = any string, ? = any char):");
    dialog.setElements(options);
    dialog.setMultipleSelection(false);
    dialog.open();
    return (String)(dialog.getFirstResult());
  }
  
  /**
   * Downloads the Jar file that corresponds to the artifact string and puts it into the
   * lib folder of the project, and adds it to the project's class path
   * @param artifactString String in the form: groupId:artifactId:version
   * @param libFolder The lib folder in the project
   */
  protected void addJarToLibFolder(String artifactString, final IFolder libFolder) {
    final String[] artifact = artifactString.split(":");
    final String groupId = artifact[0];
    final String artifactId = artifact[1];
    final String version = artifact[2];
    final String fileName = artifactId + "-" + version + ".jar";
    
    final String download = "http://search.maven.org/remotecontent?filepath="
      + groupId.replace(".", "/") + "/" + artifactId + "/" + version + "/" + fileName;
    
    final Job job = new Job("Fetching Jar file: " + download) {
      @Override
      protected IStatus run(IProgressMonitor monitor) {
        monitor.beginTask("Fetching Jar file: " + download, 100);
        monitor.worked(IProgressMonitor.UNKNOWN);
		    try {
		      // Download file
		      final IFile outFile = libFolder.getFile(fileName);
		      final InputStream inputStream = readWebpage(new URI(download));
			    final FileOutputStream outputStream = new FileOutputStream(outFile.getLocation().toFile());
			    final byte[] buffer = new byte[1024];
			    int len;
			    while ((len = inputStream.read(buffer)) != -1) {
		        outputStream.write(buffer, 0, len);
		        if (monitor.isCanceled()) {
		          outFile.delete(true, monitor);
		          log(Status.INFO, "Download cancelled: " + download);
		          return Status.CANCEL_STATUS;
		        }
			    }
			    outputStream.close();
			    
			    // Need to refresh the folder, as we changed something in the file system
			    libFolder.refreshLocal(IResource.DEPTH_ONE, null);
			    
			    // Add the jar to the project build path
	        final IPreferenceStore prefs = Activator.getDefault().getPreferenceStore();
	        if (prefs.getBoolean(PreferenceInitializer.P_ADD_TO_BUILD_PATH)) {
				    final IJavaProject javaProject = JavaCore.create(libFolder.getProject());
				    final int classpathLen = javaProject.getRawClasspath().length;
				    final IClasspathEntry[] entries = new IClasspathEntry[classpathLen + 1];
				    System.arraycopy(javaProject.getRawClasspath(), 0, entries, 0, classpathLen);
				    entries[classpathLen] = JavaCore.newLibraryEntry(outFile.getFullPath(), null, null);
				    javaProject.setRawClasspath(entries, null);
				    
				    // Refresh the project
				    libFolder.getProject().refreshLocal(IResource.DEPTH_INFINITE, monitor);
	        }
		    } catch (IOException e) {
		      log(Status.ERROR, "Could not save Jar file", e);
		      return Status.CANCEL_STATUS;
		    } catch (URISyntaxException e) {
		      log(Status.ERROR, "Invalid URI: " + download, e);
		      return Status.CANCEL_STATUS;
		    } catch (JavaModelException e) {
		      // ignore - this thing already exists in the build path
		      return Status.OK_STATUS;
		    } catch (CoreException e) {
		      log(Status.ERROR, "Could not refresh lib folder", e);
		      return Status.CANCEL_STATUS;
		    }
        monitor.done();
        return Status.OK_STATUS;
      }
    };
    job.schedule();
  }
  
  @Override
  public void selectionChanged(IAction action, ISelection selection) {
  }

  @Override
  public void setActiveEditor(IAction action, IEditorPart targetEditor) {
  }

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    run(null);
    return null;
  }
}
