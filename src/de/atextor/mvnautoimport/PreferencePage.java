package de.atextor.mvnautoimport;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public class PreferencePage
	extends FieldEditorPreferencePage
	implements IWorkbenchPreferencePage {

	public PreferencePage() {
		super(GRID);
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription("Change the Maven Auto Importer settings. Include $REPO$ in the URL where the class name should be inserted.");
	}
	
	public void createFieldEditors() {
		addField(new StringFieldEditor(PreferenceInitializer.P_MAVEN_REPO, "Maven repository URL: ", getFieldEditorParent()));
		addField(new StringFieldEditor(PreferenceInitializer.P_XPATH, "XPath to select artifacts: ", getFieldEditorParent()));
		addField(new StringFieldEditor(PreferenceInitializer.P_LIBFOLDER, "Lib folder: ", getFieldEditorParent()));
		addField(new BooleanFieldEditor(PreferenceInitializer.P_ADD_TO_BUILD_PATH, "Automatically add library to build path: ",
				getFieldEditorParent()));
	}

	public void init(IWorkbench workbench) {
	}
	
}