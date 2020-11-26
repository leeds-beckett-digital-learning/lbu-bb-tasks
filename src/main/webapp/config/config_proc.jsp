<%@ page language="java" errorPage="/error.jsp" pageEncoding="UTF-8"%>
<%@ taglib uri="/bbUI" prefix="bbUI"%>
<%@ taglib uri="/bbData" prefix="bbData"%>

<bbData:context>
    <bbUI:docTemplate title="Building Block Settings">
        <bbUI:breadcrumbBar>
        </bbUI:breadcrumbBar>
        <bbUI:titleBar iconUrl="/images/ci/icons/tools_u.gif">Building Block Settings</bbUI:titleBar>
        <%
          uk.ac.leedsbeckett.bbcswebdavmonitor.ContextListener.setConfigFromHttpRequest( request );
        %>
        <bbUI:receipt recallUrl = "/webapps/blackboard/admin/manage_plugins.jsp">
            Thank you, your settings have been saved.
        </bbUI:receipt>
    </bbUI:docTemplate>
</bbData:context>