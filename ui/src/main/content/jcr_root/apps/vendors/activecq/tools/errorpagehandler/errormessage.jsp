<%@page session="false" import="com.activecq.tools.errorpagehandler.ErrorPageHandlerService" %><%
final String path = errorPageHandlerService.findErrorPage(slingRequest, resource);
%><style><%@include file="errorpagehandler.css" %></style>
<div id="acq-eph">
    <div class="acq-eph-message">
        <h1>ATTENTION</h1>
        <p>An error occurred preventing this page from rendering. Please consult your application support team.</p>

        <p>
            In the Publish environment, this error-ing page will render using the error page: <a href="<%= path %>" target="_blank"><%= path %></a>
        </p>
        <p>
            <a href="<%= resource.getPath() %>.html?wcmmode=disabled" target="_blank">Open the requested page in Publish Mode</a>
        </p>
    </div>

    <div class="acq-eph-body">

        <div class="acq-eph-section acq-eph-collapsed" id="acq-eph-error-message">
            <h2>Error Message</h2>
            <a href="#acq-eph-error-message" class="acq-eph-toggle" data-collapse-text="Collpase error message" data-expand-text="Expand error message">Expand error message</a>
            <pre><%= pageContext.getAttribute(ErrorPageHandlerService.PAGE_ATTR_EXCEPTION) %></pre>
        </div>

        <div class="acq-eph-section acq-eph-collapsed" id="acq-eph-request-progress">
            <h2>Request Progress</h2>
            <a href="#acq-eph-request-progress" class="acq-eph-toggle" data-collapse-text="Collpase request progress" data-expand-text="Expand request progress">Expand request progress</a>
            <pre><%= pageContext.getAttribute(ErrorPageHandlerService.PAGE_ATTR_REQUESTPROGRESS) %></pre>
        </div>

    </div>

</div>
<script><%@include file="errorpagehandler.js" %></script>
</body>
</html><%
pageContext.setAttribute(ErrorPageHandlerService.PAGE_ATTR_EXCEPTION, null);
pageContext.setAttribute(ErrorPageHandlerService.PAGE_ATTR_REQUESTPROGRESS, null);
%>