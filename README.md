# Auto-Translate
A tool to automate the translation process of react/i18-next projects. <br/>
<br/>
<span style="color:red">NOTE:</span> This tool was created during a hackathon. Therefore,
code quality, tests, error handling, etc. might have suffered to get a usable version of this tool done as quickly as possible.
## Features
This tool can semi-automates the translation process by providing the following features:
1. Auto-detect new translation keys in a react/i18n-next project
2. Auto-translating translations provided by a user to all available languages
3. Posting the generated translations in a Slack channel of the users choice
4. Uploading the translation keys and generated translations to SimpleLocalize
## Requirements
Since this tool is written in Scala (and a bit of Java) it runs on the JVM. Therefore, you
must have a Java Runtime Environment (JRE) installed.
## Setup
After downloading the JAR file, you can invoke it via 
```shell
java -jar path/to/the/jar/auto-translate.jar
```
If you plan on using this tool frequently, it might be easier for you to place the following script in 
a directory that is included in your `$PATH` (e.g `/usr/local/bin`):
```shell
#!/bin/bash
java -jar path/to/the/jar/auto-translate.jar $@
```
Name this script `auto-translate` and mark it executable (via `chmod u+x auto-translate`)
You can then invoke the tool by just typing `./auto-translate`

## Usage
The tool is used in three phases:
### Initialization
When using the tool in a new project, run `auto-translate init` in the project directory.
This will create a folder `.auto-translate/` that contains the configuration file skeleton `config.json`
Before continuing, provide the necessary information in the config file.
### Snapshot
Before starting to work on the project, take a snapshot of the translation keys that are already available via
```shell
auto-translate snapshot
```
### Run
You can now add a few translations in the code and run the automated translations via 
```shell
auto-translate run
```

