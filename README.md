![Example article](assets/logo-title.png)

*Remarkable Pocket* synchronizes articles from [Pocket](https://getpocket.com) to your [Remarkable](https://remarkable.com/) tablet. It can be run on your computer or on a server. Because it does not run on the device itself this approach saves battery life, and is resistant to Remarkable software updates.

An example run of the program can be found below:

```
[2021-11-02 14:21:22] Starting sync...
[2021-11-02 14:21:25] Found 1 read article(s) on Remarkable.
[2021-11-02 14:21:25] (1/1) Marking 'Getting Unstuck' as read on Pocket...
[2021-11-02 14:21:25] (1/1) Deleting 'Getting Unstuck' from Remarkable...
[2021-11-02 14:21:27] Found 5 unread article(s) on Remarkable. Downloading 5 more from Pocket.
[2021-11-02 14:21:27] (1/5) Downloading: 'What Modules Are About'.
[2021-11-02 14:21:33] (2/5) Downloading: 'Pursue High-quality Leisure'.
[2021-11-02 14:21:39] (3/5) Downloading: 'Hunting down a C memory leak in a Go program'.
[2021-11-02 14:21:45] (4/5) Downloading: 'Beginner's Guide To Abstraction'.
[2021-11-02 14:22:03] (5/5) Downloading: 'Timer Modules in Microcontrollers'.
[2021-11-02 14:22:03] No content found. Skipping...
[2021-11-02 14:22:15] (6/5) Downloading: 'SSH Tunneling Explained'.
[2021-11-02 14:22:33] Uploading 5 article(s) to Remarkable.
[2021-11-02 14:22:33] (1/5) Uploading: 'What Modules Are About.epub'.
[2021-11-02 14:22:34] (2/5) Uploading: 'Pursue High-quality Leisure.epub'.
[2021-11-02 14:22:34] (3/5) Uploading: 'Hunting down a C memory leak in a Go program.epub'.
[2021-11-02 14:22:34] (4/5) Uploading: 'Beginner's Guide To Abstraction.epub'.
[2021-11-02 14:22:34] (5/5) Uploading: 'SSH Tunneling Explained.epub'.
[2021-11-02 14:22:35] Completed sync in 1m 13s.
[2021-11-02 14:22:35] Next sync in 30m.
```

<details><summary><i>Click here to see what a downloaded article looks like on the Remarkable.</i></summary>
<img src="assets/article-small.jpg" alt="An example article on the Remarkable.">
</details>

## Features
- **No installation required.** The application can be run with a single command.
- **Works on Remarkable 1 and 2.** 
- **Full support for images, code blocks, and formulas.**
- **Articles are downloaded as epubs.** This allows you to customize the font, font size, margins, etc.
- **Automatically archive read articles on Pocket.** When you finish reading an article and close it while on the last page, it will be automatically deleted from the Remarkable and archived on Pocket. A new unread article will be downloaded in its place.
- **Download articles from Pocket with a given tag.** If a `tag-filter` (see [Configuration](#configuration)) is supplied then only articles with that tag will be downloaded.

## Usage
The easiest way to run the application is using Docker. First install Docker for your platform from https://docs.docker.com/get-docker/. Then run the following command to start the application on Linux or Mac (I have not tested it on Windows yet):

```
touch ~/.remarkable-pocket && docker run -it --env TZ=$(date +%Z) -p 65112:65112 -v ~/.remarkable-pocket:/root/.remarkable-pocket ghcr.io/nov1n/remarkable-pocket:0.0.3
```
The first time you run the application, you will be asked to authorize Pocket and Remarkable Cloud. Once you have done this subsequent runs will read the credentials from the `~/.remarkable-pocket` file.

By default articles are synchronized to the `/Pocket/` directory on the Remarkable every 30 minutes.

*TIP:* If you want to launch the program on startup and keep it running in the background you can use *launchd* (on Mac) or *systemd* (on Linux). On Mac copy [this](nl.carosi.remarkable-pocket.plist) file to `~/Library/LaunchAgents/` followed by: `launchctl load -w nl.carosi.remarkable-pocket.plist`. Logs will be sent to `~/.remarkable-pocket.log`.


## Configuration
The default configuration can be changed by providing command-line arguments. Simply append these to the `docker run` command. Below is a list of all available options.
```
Usage: remarkable-pocket [-hnorV] [-d=<storageDir>] [-f=<tagFilter>] [-i=<interval>] [-l=<articleLimit>]
Synchronizes articles from Pocket to the Remarkable tablet.
  -f, --tag-filter=<tagFilter>
                            Only download Pocket articles with the this tag.
  -o, --run-once            Run the synchronization once and then exit.
  -n, --no-archive          Don't archive read articles.
  -l, --article-limit=<articleLimit>
                            The maximum number of Pocket articles to be present on the Remarkable.
                              Default: 10
  -i, --interval=<interval> The interval between subsequent synchronizations.
                              Default: 60m
  -r, --reset-credentials   Reset all credentials.
  -d, --storage-dir=<storageDir>
                            The storage directory on the Remarkable in which to store downloaded Pocket articles.
                              Default: /Pocket/
  -v, --verbose             Enable verbose logging.
  -h, --help                Show this help message and exit.
  -V, --version             Print version information and exit.

```

## Limitations
- Articles behind a paywall cannot be downloaded.
- Articles on websites with sophisticated DDOS protection cannot be downloaded.
- Articles that use javascript to load the the content cannot be downloaded.

## Thanks
- https://epub.press/ for providing a free epub generator API. Consider donating to support this project.
- https://github.com/jlarriba/jrmapi for providing a Java API for the Remarkable Cloud.

## Support
[!["Buy Me A Coffee"](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://www.buymeacoffee.com/nov1n) if you want to say thanks. :-)

## Disclaimer
The author(s) and contributor(s) are not associated with reMarkable AS, Norway. reMarkable is a registered trademark of reMarkable AS in some countries. Please see https://remarkable.com for their product.
