<%@page session="false"
        import="com.activecq.tools.errorpagehandler.ErrorPageHandlerService"%><%
%><%@include file="/libs/foundation/global.jsp" %><%
ErrorPageHandlerService errorPageHandlerService = sling.getService(ErrorPageHandlerService.class);

if(errorPageHandlerService != null && errorPageHandlerService.isEnabled()) {
    final int status = errorPageHandlerService.getStatusCode(slingRequest);

    if(status >= 500 && errorPageHandlerService.isAuthorModeRequest(slingRequest)) {
        if(errorPageHandlerService.isAuthorPreviewModeRequest(slingRequest)) {
            pageContext.setAttribute(ErrorPageHandlerService.PAGE_ATTR_EXCEPTION,
                    errorPageHandlerService.getException(slingRequest));
            pageContext.setAttribute(ErrorPageHandlerService.PAGE_ATTR_REQUESTPROGRESS,
                    errorPageHandlerService.getRequestProgress(slingRequest));
            %><%@include file="/apps/vendors/activecq/tools/errorpagehandler/errormessage.jsp" %><%
            return;
        }
    } else {
        slingResponse.setStatus(status);
        final String path = errorPageHandlerService.findErrorPage(slingRequest, resource);

        if(path != null) {
            sling.include(path);
            return;
        }
    }
}
%><%@include file="/libs/sling/servlet/errorhandler/default.jsp" %>