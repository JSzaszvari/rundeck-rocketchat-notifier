# rundeck-rocketchat-notifier
A RunDeck plugin that allows jobs to post Start/Sucess/Failure notifications directly into a Rocket.Chat instance in a relatively good looking manner. 


# Features
* Specify different channels for Success/Failure notifications.  
* Different colour notifications for Success/Fail/Start.
* Several kinds of messages supported by different templates.

# Installing
* Build the JAR from source.
* Place the `.jar` into your RunDeck plugins folder located at `$RDECK_BASE/libext/`, by default `/var/lib/rundeck/libext/`.
* Restart RunDeck.

# Configuration
* Set up a Incoming Integration in Rocket.Chat and take note of the URL.
* Turn on notifications in the Job.
* Put the URL in the 'Rocket.Chat WebHook URL' field .
* Fill in the name of the channel you want the notifications to go to.
* (Optional) Chose a template. The default template is `rocket-chat-incoming-message.ftl`.

# Building from Source

This works fine with openjdk-8-jre and openjdk-8-jre works fine in Ubuntu 20.04.04 LTS (focal):

### On Ubuntu 20 example:

Install openjdk-8-jre:

```sh
$ sudo apt install openjdk-8-jre
```

Install openjdk-8-jdk:

```sh
$ sudo apt install openjdk-8-jdk
```
### Clone and build:

```sh
$ git clone https://github.com/JSzaszvari/rundeck-rocketchat-notifier.git
$ cd rundeck-rocketchat-notifier
```

Use ./gradlew at local folder to build and run with gradle 1.10 (will be download and exec in build process)

```sh
$ ./gradlew build
```
Once the build is complete the compiled .jar will be in be in the build/libs folder. Initial build should take a few minutes to run, subsequent builds should take a few seconds.

### Screen-Shots:
#### Notifications
![Example Notifications](https://github.com/jszaszvari/rundeck-rocketchat-notifier/blob/master/example.png "Example Notification")

#### Configuration
![Example Config](https://github.com/jszaszvari/rundeck-rocketchat-notifier/blob/master/config.png "Example Config")

