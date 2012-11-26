/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.activecq.tools.errorpagehandler;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;

/**
 *
 * @author david
 */
public interface ErrorPageHandlerService {
    public static final String STATUS_CODE = "javax.servlet.error.status_code";
    public static final String SERVLET_NAME = "javax.servlet.error.servlet_name";
    public static final String PAGE_ATTR_EXCEPTION = "com.activecq.tools.errorpagehandler.keys.exception";
    public static final String PAGE_ATTR_REQUESTPROGRESS = "com.activecq.tools.errorpagehandler.keys.requestprogress";
    public static final int DEFAULT_STATUS_CODE = 500;

    public String findErrorPage(SlingHttpServletRequest request, Resource errorResource);
    public int getStatusCode(SlingHttpServletRequest request);
    public String getErrorPageName(SlingHttpServletRequest request);
    public String getSystemErrorPagePath();
    public String getErrorPagesPath(String path);
    public String getErrorPageExtension();
    public boolean isEnabled();

    public boolean isAuthorModeRequest(SlingHttpServletRequest request);
    public boolean isAuthorPreviewModeRequest(SlingHttpServletRequest request);

    public void doHandle404(SlingHttpServletRequest request, SlingHttpServletResponse response);

    public String getException(SlingHttpServletRequest request);
    public String getRequestProgress(SlingHttpServletRequest request);
}
