<%@page session="false" import="com.activecq.tools.errorpagehandler.ErrorPageHandlerService" %><%
%><%@include file="/libs/foundation/global.jsp" %><%

ErrorPageHandlerService errorPageHandlerService = sling.getService(ErrorPageHandlerService.class);
if(errorPageHandlerService == null || !errorPageHandlerService.isEnabled()) { return; }

final String stackTrace = errorPageHandlerService.getException(slingRequest);
final String requestProgress = errorPageHandlerService.getRequestProgress(slingRequest);
final String path = errorPageHandlerService.findErrorPage(slingRequest, resource);

// Clear the response
slingResponse.reset();

%>
<html>
<head>
    <title>Adobe CQ - Error Page</title>
    <style>
        <%@include file="css/normalize.css" %>
        <%@include file="css/errorpagehandler.css" %>
    </style>
</head>
<body>
<div id="acq-eph">
    <div class="acq-eph-message">
        <h1>ATTENTION</h1>

        <p>
            An error occurred preventing this page from rendering. Please consult your application support team.
        </p>

        <p>
            In the Publish environment, this erroring page will render using the error page: <a href="<%= path %>" target="_blank"><%= path %></a>
        </p>

        <p>
            <a href="<%= resource.getPath() %>.html?wcmmode=disabled" target="_blank">Open the requested page in Publish Mode</a>
        </p>
    </div>

    <div class="acq-eph-body">
        <div class="acq-eph-section acq-eph-collapsed" id="acq-eph-error-message">
            <h2>Error Message</h2>
            <a href="#acq-eph-error-message" class="acq-eph-toggle" data-collapse-text="Collapse error message" data-expand-text="Expand error message">Expand error message</a>
            <pre><%= stackTrace %></pre>
        </div>

        <div class="acq-eph-section acq-eph-collapsed" id="acq-eph-request-progress">
            <h2>Request Progress</h2>
            <a href="#acq-eph-request-progress" class="acq-eph-toggle" data-collapse-text="Collapse request progress" data-expand-text="Expand request progress">Expand request progress</a>
            <pre><%= requestProgress %></pre>
        </div>
    </div>

</div>
<script><%@include file="js/jquery.min.js" %></script>
<script><%@include file="js/errorpagehandler.js" %></script>
</body>
</html>