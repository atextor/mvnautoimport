package de.atextor.mvnautoimport;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

public class PreferenceInitializer extends AbstractPreferenceInitializer {
	public static final String P_MAVEN_REPO = "maven_repo";
	public static final String P_XPATH = "xpath";
	public static final String P_LIBFOLDER = "libfolder";
	public static final String P_ADD_TO_BUILD_PATH = "addtobuildpath";
	
	public static final String DEFAULT_MAVEN_REPO = "http://search.maven.org/solrsearch/select?q=c:%22$REPO$%22&rows=20&wt=xml";
	public static final String DEFAULT_XPATH = "//lst/lst/@name";
	public static final String DEFAULT_LIBFOLDER = "lib";
	public static final boolean DEFAULT_ADD_TO_BUILD_PATH = true;
	
	public void initializeDefaultPreferences() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		store.setDefault(P_MAVEN_REPO, DEFAULT_MAVEN_REPO);
		store.setDefault(P_XPATH, DEFAULT_XPATH);
		store.setDefault(P_LIBFOLDER, DEFAULT_LIBFOLDER);
		store.setDefault(P_ADD_TO_BUILD_PATH, DEFAULT_ADD_TO_BUILD_PATH);
	}

}
