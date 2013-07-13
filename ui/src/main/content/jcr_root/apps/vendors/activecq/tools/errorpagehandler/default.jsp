<%@page session="false"
        buffer="512kb"
        import="com.activecq.tools.errorpagehandler.ErrorPageHandlerService"%><%
%><%@include file="/libs/foundation/global.jsp" %><%
ErrorPageHandlerService errorPageHandlerService = sling.getService(ErrorPageHandlerService.class);

if(errorPageHandlerService != null && errorPageHandlerService.isEnabled()) {
    final int status = errorPageHandlerService.getStatusCode(slingRequest);

    if(status >= 500 && errorPageHandlerService.isAuthorModeRequest(slingRequest)) {
        if(errorPageHandlerService.isAuthorPreviewModeRequest(slingRequest)) {
            %><cq:include script="preview/errormessage.jsp" /><%
            return;
        } else {
            // In Author and Edit or Design, so allow OOTB WCMDebugFilter to handle the error message display
            return;
        }
    } else {
        slingResponse.setStatus(status);
        final String path = errorPageHandlerService.findErrorPage(slingRequest, resource);

        if(path != null) {
            errorPageHandlerService.resetRequestAndResponse(slingRequest, slingResponse, status);
            sling.include(path);
            return;
        }
    }
}
%><%@include file="/libs/sling/servlet/errorhandler/default.jsp" %>