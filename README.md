# Clusters

> **AI Usage Disclaimer**
> 
> Since I'm not an Android developer, I've used AI-based tools to produce the code in this repository. By using this repo, you agree to review the code you're using, to apply your judgment, and to use critical thinking.

> **Responsibility Disclaimer**
> 
> This repository is a hobby project, and is provided with no guarantee. By using this repo, you agree to the risks of running custom software on proprietary hardware (such as a vehicle's head unit). Furthermore, the content provided throught marketplaces (official and third-party repositories) may have been created by external users, and may not have been extensively reviewed. **Custom display screen implementations execute third-party, arbitrary code, and can pose a security threat to your device.**

Clusters is an Android app, which is meant to be installed on the Android-based head unit of 10th generation Honda Civic models (2016-2021). The app runs on the Android 4.2.2 head unit, and overrides display on the external display, which is the instrument cluster's info screen (in some contexts).

When started, the app send to the cluster the signal that it's now rendering a display (overriding the cluster's info display). From there, the app uses the main display so the user can control what's shown in the cluster screen. When the app exits, the rendering is stopped.

## Current State of the Repo

Right now, there are still many artifacts of the repo's creation. The app has debug screens all over, there is not enough interfaces for features specific to some car models, the main app's structure is too heavy, and many of the exposed vehicle attributes don't yet work. This will change as I get more time to clean up and work on the repository, but in the meantime, it's being shared this way for others to explore too. Please see `CONTRIBUTING.md`.
