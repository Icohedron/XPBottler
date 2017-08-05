# XPBottler
Store your experience in bottles!  
*Still in development/testing*

# WARNING: avoid using this plugin (for now)
There are several bugs in the way Sponge handles player experience data which may cause experience to be completely inacurrate or change unpredictably. Read more about it [here](https://github.com/Icohedron/XPBottler/issues/2)

## Features
- Create experience bottles (Bottle o' Enchanting) to store player experience in
- Configurable amount of experience required to create an experience bottle (Bottle o' Enchanting)
- Option to consume a glass bottle per experience bottle (Bottle o' Enchanting) created

## Permissions
```
xpbottler.command.bottle
```

## Commands
```
# Take your experience (and optionally, glass bottles) and create experience bottles (Bottle o' Enchanting) out of them
/bottle [amount]
/bottle max
```

## Default Configuration File
```
# Amount of experience consumed when creating an experience bottle (Bottle o' Enchanting)
# F.Y.I. Experience bottles (Bottle o' Enchanting) give 3-11 experience each
# A value of 11 ensures that players will never get more experience than what they originally bottled, but will almost always get less xp back than what they bottled.
# A value of 7 will average out to give the player approximately the same amount of experience they put in bottles.
xp_per_bottle: 11

# Whether or not to consume glass bottles when creating an experience bottle (Bottle o' Enchanting)
consume_bottles: true
```
