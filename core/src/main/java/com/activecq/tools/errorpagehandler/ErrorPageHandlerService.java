/*
 * Copyright 2013 david gonzalez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    public static final int DEFAULT_STATUS_CODE = 500;

    public String findErrorPage(SlingHttpServletRequest request, Resource errorResource);
    public int getStatusCode(SlingHttpServletRequest request);
    public String getErrorPageName(SlingHttpServletRequest request);
    //public String getSystemErrorPagePath();
    //public String getErrorPagesPath(String path);
    //public String getErrorPageExtension();
    public boolean isEnabled();

    public boolean isAuthorModeRequest(SlingHttpServletRequest request);
    public boolean isAuthorPreviewModeRequest(SlingHttpServletRequest request);

    public void doHandle404(SlingHttpServletRequest request, SlingHttpServletResponse response);

    public String getException(SlingHttpServletRequest request);
    public String getRequestProgress(SlingHttpServletRequest request);
}
