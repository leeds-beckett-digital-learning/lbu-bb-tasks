# lbu-bb-tasks
Blackboard Building Block that provides sysadmin tasks for managing files on the server.
## Warning
You should only run a task provided by this tool on your Blackboard installation after you have studied the source code for the task and understand how it works.
This is because a task may delete files on the server in a way that has side effects. If you install a new version of the tool you need to study the changes to
a task before running it again. It's worth repeating that the authors/copyright holders of this source code accept no liability if your use of the tool causes
damage to your data. This is made clear by the Apache 2 license under which this software is released.
## Intro
This tool was created to find out why we were paying for such a huge consumption of file space on our Blackboard Learn service. Since our installation is a System
As A Service, (SAAS) we don't have administrative log in's to the servers which run Blackboard learn we needed to find another way to generate analyses of filing
systems. A Blackboard Building Block can be designed that can examine files both in the server's underlying file system and in the content collection (Xythos) file
repository.

Having analysed file usage we have worked on creating various tasks that we can use to reduce file usage.

##Turnitin Building Block
One of the issues that we discovered was massively affecting our consumption of file space relates to our use of Turnitin's Blackboard Building Block. (As
distinct from using Turnitin's LTI integration.) We stuck with the building block because we don't beleive that the LTI integration is reliable yet.

However, using the tasks tool we discovered that the Turnitin building block was putting student's files onto the Blackboard Learn server's file space before
uploading it to Turnitin's own servers. These files are never deleted but also never used! The space taken up by these files multiplies because they get packed 
into autoarchives of the courses they belong to.

A task provided by this tool will delete all of these Turnitin files in all courses. If this is run often enough it can drastically reduce file space consumption.
