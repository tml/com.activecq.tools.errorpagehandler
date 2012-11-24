<%@page session="false" import="com.activecq.tools.errorpagehandler.ErrorPageHandlerService" %><%
%><style><%@include file="errorpagehandler.css" %></style>
<div id="acq-eph">
    <div class="acq-eph-message">
        <h2>Attention</h2>
        <p>An error occurred preventing this page from rendering.</p>
        <p>Please consult your application support team.</p>
    </div>

    <div class="acq-eph-section acq-eph-collapsed">
        <h2>Error Message</h2>
        <a href="#" class="acq-eph-toggle" data-collapse-text="Collapse" data-expand-text="Expand">Expand</a>
        <pre><%= pageContext.getAttribute(ErrorPageHandlerService.PAGE_ATTR_EXCEPTION) %></pre>
    </div>

    <div class="acq-eph-section acq-eph-collapsed">
        <h2>Request Progress</h2>
        <a href="#" class="acq-eph-toggle" data-collapse-text="Collapse" data-expand-text="Expand">Expand</a>
        <pre><%= pageContext.getAttribute(ErrorPageHandlerService.PAGE_ATTR_REQUESTPROGRESS) %></pre>
    </div>
</div>
<script><%@include file="errorpagehandler.js" %></script>
</body>
</html><%
pageContext.setAttribute(ErrorPageHandlerService.PAGE_ATTR_EXCEPTION, null);
pageContext.setAttribute(ErrorPageHandlerService.PAGE_ATTR_REQUESTPROGRESS, null);
%>