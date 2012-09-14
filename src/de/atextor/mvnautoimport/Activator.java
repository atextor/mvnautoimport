package de.atextor.mvnautoimport;

import org.eclipse.core.net.proxy.IProxyService;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

public class Activator extends AbstractUIPlugin {
	private static Activator plugin;
	private ServiceTracker tracker;
	
	public Activator() {
	  plugin = this;
	}

	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		
	  tracker = new ServiceTracker(getBundle().getBundleContext(), IProxyService.class.getName(), null);
	  tracker.open();
	}

	public void stop(BundleContext context) throws Exception {
		plugin = null;
		tracker.close();
		super.stop(context);
	}

	public static Activator getDefault() {
		return plugin;
	}
	
	public IProxyService getProxyService() {
	  return (IProxyService)tracker.getService();
	}
}
