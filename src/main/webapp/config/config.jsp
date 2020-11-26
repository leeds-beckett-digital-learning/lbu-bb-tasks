<%@page errorPage="/error.jsp"%>

<%@ taglib uri="/bbUI" prefix="bbUI"%>
<%@ taglib uri="/bbNG" prefix="bbNG"%>
<%@ taglib uri="/bbData" prefix="bbData"%>


<%
String runlevelopt ="Off";
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<bbNG:genericPage title="Building Block Settings">

  <bbNG:pageHeader>
    <bbNG:pageTitleBar>
      Building Block Settings
    </bbNG:pageTitleBar>
  </bbNG:pageHeader>

  <form name="config" action="config_proc.jsp" method="POST" onsubmit="javascript: return validateForm(this);">

    <bbNG:dataCollection>
      <bbNG:step title="Processing"> 
        <bbNG:dataElement label="runlevel" isRequired="true">
          <bbNG:radioElement title="Off"        helpText="Do nothing."           name="runlevelopt" value="Off"        optionLabel="Off"        isVertical="True" isSelected="<%= (runlevelopt.equals(\"Off\")        ? Boolean.TRUE : Boolean.FALSE)%>"/>
          <bbNG:radioElement title="Permissive" helpText="Log only."             name="runlevelopt" value="Permissive" optionLabel="Permissive" isVertical="True" isSelected="<%= (runlevelopt.equals(\"Permissive\") ? Boolean.TRUE : Boolean.FALSE)%>"/>
          <bbNG:radioElement title="Strict"     helpText="Prevent some uploads." name="runlevelopt" value="Strict"     optionLabel="Strict"     isVertical="True" isSelected="<%= (runlevelopt.equals(\"Strict\")     ? Boolean.TRUE : Boolean.FALSE)%>"/>
        </bbNG:dataElement>
      </bbNG:step>

      <bbNG:stepSubmit instructions="Click <b>Submit</b> to save or update the settings. "/>

    </bbNG:dataCollection>
  </form>
</bbNG:genericPage>