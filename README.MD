# RSPS NPC/Object SpawnEditor by Xeon
![Image](https://hellstxr.b-cdn.net/jiOfORRBmq.png)

This is the source code release, if you're looking for the app itself, see [HERE](https://rune-server.org/threads/spawn-editor-for-matrix-bases.707876/#post-5820353)
- Extra classes for Matrix servers and .JSON conversion tool can be found in `Server_Classes_&_JSON_Tools.rar`
--------------------------------------------------------------------------------------------------------------------------------------
Build Requirements:
- JDK 17
- Gradle 8.9
- FlatLaf 2.6 (Downloaded by gradle)
- Gson 2.8.9 (Downloaded by gradle)
- Discord Game SDK v1.0.0 (Included in /lib/)

Usage Requirements:
- Valid spawns.json, npcs.json, and objects.json
- Map.png from [OpenRS2](https://archive.openrs2.org/caches)
--------------------------------------------------------------------------------------------------------------------------------------
Release text from Rune-Server Thread:
--------------------------------------------------------------------------------------------------------------------------------------
Hello, this is a RSPS NPC/object spawn editor tool, made to make RSPS world building on Matrix bases a little bit more interactive.

- This is a purpose built tool which will likely require some configuration to work as expected.
- This document will serve as a tutorial/guide on how to make your server compatible with the editor.
- Read the entire thing carefully, as there is lots of important info and missing a single step can cause it to not work as expected.
- This expects you to have at least SOME knowledge of how to execute existing scripts and copy/paste files and code snippets.

--------------------------------------------------------------------------------------------------------------------------------------

With this release, I have provided some classes for the purpose of creating the needed files,
and one for wiring the JSON spawning and editor integration into your server.

- **NPCListDumper** and **ObjectListDumper** are self descriptive, more info below.

- **NPCSpawning** will be the core of your server's JSON spawn parsing, which the editor can directly modify,
  the `spawns.json` used by the editor is read by the provided `NPCSpawning` class you will need to use.

- The dumper classes can be placed inside your Matrix server's `com.rs.tools` (or similar) package, and executed from
  your `main()` method that starts your server, often in `Launcher.java` or `Loader.java`.

- They will dump your cache's NPC and object lists to compatible JSON that is readable by the spawn editor, and will populate the
  editor's lefthand lists with names and IDs.

Example list JSON files have been included, inside `/resources/` named `objects.json` and `npcs.json`.

--------------------------------------------------------------------------------------------------------------------------------------

The python file in the `/java2json/` folder is designed for bases with static NPC and object spawns in the following formats:

--------------------------------------------------------------------------------------------------------------------------------------

- `World.deleteObject(new WorldTile(X, Y, Z));`
- `World.deleteObject(new WorldObject(ID, TYPE, ROT, X, Y, Z));`
- `World.spawnObject(new WorldObject(ID, TYPE, ROT, X, Y, Z), TF);`
- `World.spawnOverObject(new WorldObject(ID, TYPE, ROT, X, Y, Z), TF);`
- `World.spawnNPC(ID, new WorldTile(X, Y, Z), WALK_RADIUS_#, AGGRESSIVE_TF, EntityDirection.DIRECTION); //NORTH/NORTHWEST/ETC`

-----------------------
[ TF/_TF = true/false ]  
[ EntityDirection.DIRECTION must be capitalized, as in EntityDirection.NORTH ]

As well as delete object loops, such as:

- `for (int x = coordX1; x <= coordX2; x++) { for (int y = coordY1; y >= coordY2; y--) { World.deleteObject(new WorldTile(x, y, z)); } }`

It could be expanded to search for loops of other spawn types but this was not needed for my purposes at the time.

---------------------------------------------------------------------------------------------------------------------------------------

- The `java2json` python file's purpose is to output valid JSON for use with the main editor tool.
  It is only needed if you already have a `NPCSpawning` file or one with the lines listed above.

- It accepts an input file, and will parse using regex pattern matching, outputting to JSON that is readable by the editor.
  An example of the JSON has been included, inside `/resources/` named `spawns.json`.

- After running this script (if applicable),
  you may now replace any existing `NPCSpawning` class with the one provided in this release. It will load `spawns.json` from your `/data/` folder.
  (!!! MAKE A BACKUP OF YOUR FILES FIRST BEFORE REPLACING ANYTHING, AS ALWAYS !!!!)

- Before you ask, yes, it can still spawn objects, that is just the naming scheme my server's base came with before I converted it to this.
  It will live in your Matrix server's `com.rs.utils` (or similar) package and is initialized in `com.rs.game.World`, inside the `init()` method.
  This will be different than what you may have already had, so pay attention:

---------------------------------------------------------------------------------------------------------------------------------------

You will be adding some code to the previously mentioned World class. 

Example snippet from `World.java`:

```java
import com.rs.utils.NPCSpawning; //Add this to the top with other imports if not already present

public static final void init() {
    addTriviaBotTask();
    addQuickTask();
    addTipsTask();
    spawnStar();
    addRestoreRunEnergyTask();
    addDrainPrayerTask();
    addRestoreHitPointsTask();
    addRestoreSkillsTask();
    addRestoreSpecialAttackTask();
    addRestoreShopItemsTask();
    addSummoningEffectTask();
    addOwnedObjectsTask();
    LivingRockCavern.init();
    NPCSpawning.npcSpawn(); // THIS LINE MUST BE ADDED TO USE THE PROVIDED CLASS!!
    PuroPuro.initPuroImplings();
}
```

---------------------------------------------------------------------------------------------------------------------------------------

- If you did not previously have the `NPCSpawning` class and have just added it, you will *not* have any spawns from it visible in the
  editor. However, you can now use the editor to add spawns to this system, as it works based on JSON and not the original static java
  system used by the base I started with. This should get you going on adding the needed functionality to your server to use this editor.

- The provided `NPCSpawning` class contains hot-reload methods for the now JSON loaded spawns.* You can use this to make commands to reload
  spawns without server restarts. I cannot provide the command as each server's setup is slightly different. If you're stuck with
  this step, try google or AI. I will not be providing coding support beyond the scope of the tool itself.

*DISCLAIMER - The object hot-reload methods are buggy, it does not seem to properly send the destroy object packet, at least on my server.
It's suggested you reload NPCs and objects separately as the object reloading may not update to clients and will require a server restart.

---------------------------------------------------------------------------------------------------------------------------------------

Lastly, the editor tool can handle the old-style Matrix text-based serialized spawns for NPCs. I did not include serialized object spawn
support and there's simply no reason to use it with the JSON system we have with this tool. 

The serialized NPC spawn part in the editor labels them "Dynamic" and they appear as pink dots. Adding/editing/deleting them will update
the text file with new entries, but you must still repack the spawns the old fashioned way.

---------------------------------------------------------------------------------------------------------------------------------------

Checklist:
- Dump NPC and object spawns to JSON.
- If applicable, scrape static spawns from Java -> JSON.
- Add/replace NPCSpawning class & call in World class.
- Place spawns.json in /data/ (you may use mine as a starter)

---------------------------------------------------------------------------------------------------------------------------------------

Any further questions, concerns, or feedback should be brought to my attention via discord, you can find me in my own server's discord:
- https://discord.gg/dqTN9fax6s


