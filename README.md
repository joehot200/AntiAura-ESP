# AntiAura-ESP

**Greetings!**


AntiAura stopped blocking ESP because:

1) The code was/is getting messy
2) It was becoming a headache to maintain
3) There are "more important" hacks to block (e.g. combat, movement).
4) Open-sourcing meaning you, the community, can help with bugfixes/updates! (For example: Want to block ChestESP? I have some old code for that!)

If you'd like to tidy up some code, please feel free! There is also no config yet.... if you'd like to add one, please do!


By default, AntiAura-ESP gets the material by simply checking the block using Block.getType(). 

If AntiAura is installed, it will hook into the async ground checker of AntiAura using the API, getting blocks async & offloading CPU onto another thread.

# Credit

The EntityHider class is modified from aandk/dmulloy2's EntityHider class from somewhere.
