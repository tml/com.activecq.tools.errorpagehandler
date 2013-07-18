<%@page session="false"
        import="com.activecq.tools.errorpagehandler.ErrorPageHandlerService"%><%
%><%@include file="/libs/foundation/global.jsp" %><%
ErrorPageHandlerService errorPageHandlerService = sling.getService(ErrorPageHandlerService.class);

if(errorPageHandlerService != null && errorPageHandlerService.isEnabled()) {
    final int status = errorPageHandlerService.getStatusCode(slingRequest);

    if(status >= 500 && errorPageHandlerService.isAuthorModeRequest(slingRequest)) {
        if(errorPageHandlerService.isAuthorPreviewModeRequest(slingRequest)) {
            %><cq:include script="/apps/vendors/activecq/tools/errorpagehandler/preview/errormessage.jsp" /><%
            return;
        } else {
            // In Author and Edit or Design, so allow OOTB WCMDebugFilter to handle the error message display
            return;
        }
    } else {
        final String path = errorPageHandlerService.findErrorPage(slingRequest, resource);

        if(response.isCommitted()) {
            log.error("Unable to serve user a valid 500 error page due to a committed response: " + slingRequest.getRequestURL());

            // Hack to handle committed response; meta-refresh to the error page.
            // Try to close any potential open tags first, especially script tags.
            // Mark-up will be mangled; however Response will return with a 500 (non-caching error code)
            String metaRedirect = "/></script>";
            metaRedirect += "<meta http-equiv=\"refresh\" content=\"0; url=" + path + "/\">";

            out.print(metaRedirect);
            out.flush();
            return;
        } else {
            slingResponse.setStatus(status);

            if(path != null) {
                errorPageHandlerService.resetRequestAndResponse(slingRequest, slingResponse, status);
                sling.include(path);
                return;
            }
        }
    }
}
%><%@include file="/libs/sling/servlet/errorhandler/default.jsp" %>