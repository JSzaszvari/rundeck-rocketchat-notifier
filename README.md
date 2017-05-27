# rundeck-rocketchat-notifier
A RunDeck plugin that allows jobs to post Start/Sucess/Failure notifications directly into a Rocket.Chat instance. 

Unofortonatly the "WebHook" notifications in RunDeck did not work properly as the implementation is different. 


# Features
* Specify different channels for Success/Failure notifications.  
* Pretty notifications (maybe)

# Installing
* The quickest/easiest way is to get the pre-compiled .jar from the GitHub Releases page -  [The latest can be found here](https://github.com/jszaszvari/rundeck-rocketchat-notifier/releases/download/v0.1/rundeck-rocketchat-notifier-0.1.jar) 
* Place the .jar into your RunDeck plugins folder located at $RDECK_BASE/libext/ - The default Debian/Ubuntu plugin folder for RunDeck should be /var/lib/rundeck/libext/
* Restart RunDeck

# Building from Source
```sh
$ git clone git@github.com:jszaszvari/rundeck-rocketchat-notifier.git
$ cd rundeck-rocketchat-notifier
$ gradle build
```
Once the build is complete the compiled .jar will be in be in the build/libs folder. Initial build should take a few mins to run, subsequent builds should take a few seconds.

# Configuring
* Set up a Incoming Intergration in Rocket.Chat and take note of the URL
* Put the URL in the 'Rocket.Chat WebHook URL' field 
* Fill in the name of the channel you want the notifications to go too. 

See the image below for an example
![Example Config](https://github.com/jszaszvari/rundeck-rocketchat-notifier/blob/master/config.png "Example Config")
