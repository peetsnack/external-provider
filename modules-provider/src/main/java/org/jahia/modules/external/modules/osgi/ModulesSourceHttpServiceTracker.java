package org.jahia.modules.external.modules.osgi;

import com.phloc.commons.io.file.filter.FilenameFilterNotEquals;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.bundles.extender.jahiamodules.BundleHttpResourcesTracker;
import org.jahia.bundles.extender.jahiamodules.FileHttpContext;
import org.jahia.data.templates.JahiaTemplatesPackage;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.render.scripting.bundle.BundleScriptResolver;
import org.jahia.services.templates.TemplatePackageRegistry;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class ModulesSourceHttpServiceTracker extends ServiceTracker {
    private static Logger logger = LoggerFactory.getLogger(ModulesSourceHttpServiceTracker.class);

    private final Bundle bundle;
    private final String bundleName;
    private final JahiaTemplatesPackage module;
    private final BundleScriptResolver bundleScriptResolver;
    private final TemplatePackageRegistry templatePackageRegistry;
    private HttpService httpService;

    public ModulesSourceHttpServiceTracker(JahiaTemplatesPackage module) {
        super(module.getBundle().getBundleContext(), HttpService.class.getName(), null);
        this.bundle = module.getBundle();
        this.bundleName = BundleUtils.getDisplayName(bundle);
        this.module = module;
        this.bundleScriptResolver = (BundleScriptResolver) SpringContextSingleton.getBean("BundleScriptResolver");
        this.templatePackageRegistry = (TemplatePackageRegistry) SpringContextSingleton.getBean("org.jahia.services.templates.TemplatePackageRegistry");
    }

    @Override
    public Object addingService(ServiceReference reference) {
        HttpService httpService = (HttpService) super.addingService(reference);
        this.httpService = httpService;
        return httpService;
    }

    public void registerJsp(File jsp) {
        String jspPath = getJspPath(jsp);
        if (bundle.getEntry(jspPath) != null) {
            return;
        }
        unregisterJsp(jsp);
        String jspServletAlias = "/" + bundle.getSymbolicName() + jspPath;
        HttpContext httpContext = new FileHttpContext(FileHttpContext.getSourceURLs(bundle),
                httpService.createDefaultHttpContext());
        BundleHttpResourcesTracker.registerJspServlet(httpService, httpContext, bundle, bundleName, jspServletAlias, jspPath, null);
        bundleScriptResolver.addBundleScript(bundle, jspPath);
        templatePackageRegistry.addModuleWithViewsForComponent(StringUtils.substringBetween(jspPath, "/", "/"), module);
        if (logger.isDebugEnabled()) {
            logger.debug("Register JSP {} in bundle {}", jspPath, bundleName);
        }
    }

    public void unregisterJsp(File jsp) {
        String jspPath = getJspPath(jsp);
        String jspServletAlias = "/" + bundle.getSymbolicName() + jspPath;
        httpService.unregister(jspServletAlias);
        bundleScriptResolver.removeBundleScript(bundle, jspPath);
        String propertiesFileName = FilenameUtils.removeExtension(jsp.getName()) + ".properties";
        if (jsp.getParentFile().listFiles(new FilenameFilterNotEquals(propertiesFileName)).length == 0) {
            templatePackageRegistry.removeModuleWithViewsForComponent(StringUtils.substringBetween(jspPath, "/", "/"), module);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Unregister JSP {} in bundle {}", jspPath, bundleName);
        }
    }

    protected String getJspPath(File jsp) {
        return StringUtils.substringAfterLast(FilenameUtils.separatorsToUnix(jsp.getPath()),"/src/main/resources");
    }
}
