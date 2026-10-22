ability to import pictures into the app. importing creates a copy of the image and keeps it in the local repo. 
- when importing, there should be an potion to add tag/tags to all images that are being imported

importing options
- add tag(s) to all items being imported at once
- create a copy of move the images
- remove image from the import stage
- check if image already exists. give warning

have a "gallery" which is just all the images in the custom folder. you can open up an image to view it or also just use this tab to manage all the images you have. 
- should have a filter by tag
- search by name
- filter by import date etc
- grid view 
- list view
- favorite images & favorites tab
- allow you to tag items / delete items 

tagging is one of the main benefits of the app. you can supply any amount of tags to an image. when you're doing practice, you can say you only want these tags for example.
- default
- in typical apps like this, they have tags such as male/female or nude/covered, etc... the downsize of my app would be having to set these up yourself, but ultimately is much more flexible and customizable. 
- filters should have AND, OR, and NOT. filters. most important is NOT so you can do like NOT nude for example. 
- tags are hierarchical. ex you can have a "pose" tag. it can have child tags of "laying", "jumping", etc... or "animal" > "dog" > "husky". if you set the tag to animal itll include everything that is a sub tag to it as well. 

in the practice mode, there are intervals for how long to see the image before going next. 
- not timed
- 30s
- 60s/1min
- 2min
- 3min
- 5min
- 10min
- custom

options during practice
- play/pause timer
- previous
- next
- exit session
- vertical flip
- horizontal flip
- grayscale toggle

also during practice you can:
- zoom into image
- 

maybe have a way to build your own queue instead of just random? this would have to be an alternative to tag filters for random. random practice vs set practice. queue practice could be a couple thing:
- playlist: group of N images. can be sequential based on the order you added them or random.
- queue: you set a filter for n images, you set another filter for n images, etc... (5 female images, 5 male images, 5 dog images, etc..). basically random but dynamic. can have timer on each section so you can ramp up.
- random: random practice is just 1 filter that keeps going
- favorites: basically playlist but the image set is your favorited items

**Simplify the practice modes into one model.** Playlist, queue, random, and favorites are really the same thing. A session is a list of segments, and each segment has a source (a tag filter, a playlist, or favorites), a count, an order (random or sequential), and a timer. Random is one segment with an unlimited count. Favorites is a filter (`is:favorite`). Your “5 female, 5 male, 5 dog” queue is three segments. This is much simpler to build and gives users more power.

when you do the practice mode, you can select how many images that you want to go throug has well. starts on unlimited but other options include 5, 10, etc...

there should be preconfigured settings configs that you can select so you can easily do practice sessions. these configs hold:
- tag filters / image queue
- how many images
- timer per image

keyboard shortcuts
- space to pause/resume
- l-arrow: go back
- r-arrow: go next
- esc: quit session

make an export feature which exports all ur images and a metadata file so you can switch between desktops and not have to manually redo everything if you wanted to. 

deleting things moves to ur recycle bin. you can choose where the local folder is. 

use a database to handle as much as i can. 

make it easy as possible to start practicing and get up to the practice app. the home screen should have a few couple of presets or something to jump right into practice.

save when an image was last seen in practice mode. add an option to only pick images which havent been seen in x amount of time. 

memory drawing practice. show the image for n seconds then hide for n seconds. show the image again when done to compare vs what you drew. 

when timer ticks what is behavior?
- immediate go next
- go next after n seconds (break)
- click to go to the next when ready
