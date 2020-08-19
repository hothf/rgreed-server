<div align="center">
<img src="./icon.png" width="150" height="150" />

<h1 align="center">
    Rgreed Server
</h1>

</div>

This backend written in **Kotlin** uses the **Ktor**-Framework for offering a *REST-Like* api to access a **PostgreSQL** Database for storing and managing consensus.

The system behind consensus is called [Systematisches Konsensieren](http://www.sk-prinzip.eu/).


## Build

It is recommended to use Intellij for building the project. It uses the [Ktor](https://ktor.io/) framework.

Look for the `Application.kt` file to run the `module()` method.

A default in memory database driver is used when deployed on the machine.

**Note:**: The project should not find the initial properties for the remote database as it has been removed to make this project a simple showcase only.

## Architecture

Main entry point is the `Application.kt` file. This installs every feature needed and configures database access.
The further architecture consists of three layers:

1. We use a layer to abstract from the database, called *Repository*. Access to tables, each suffixed with *dao* should always be made through these. All these classes belong into the `dao` package.
2. The next layer is the abstraction of api routes through different routing files, defined ultimately in `Route.kt` file, all residing in the `route package.
3. Api models are abstracted from database models exclusively in the `model` package.

We use integration tests for all routes and database repositories inside the `test
 package
## Extension

Adding new api routes is as easy as adding new `route() methods to the Route.kt` class. Please provide extension functions for the routes to keep the structure clean.

## Contribute

Use pull requests to merge into the *develop* branch and open issues of the GitLab project to help contributing:
- We use a Git-Flow approach, naming the develop branch *stable*. Please branch from this to work on new features.
- Please make sure to add **Tests** to all new features you want to be merged into the develop branch.
