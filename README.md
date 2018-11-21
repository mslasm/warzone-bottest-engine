# warzone-bottest-engine

A modified https://github.com/theaigames/warlight2-engine engine.

All changes were done with the following two goals in mind:

1) make engine and game setups more compatible with the actual WarZone server:
  - ability to read maps as provided by Query Game API (https://www.warzone.com/wiki/Query_game_API)
  - support for more map settings: "no luck" and different rounding modes, different starting armies and wastelands, different kill ratios, etc.

2) make testing bots locally as easy and as fast as possible:
  - support for fixed random seeds for all random generators
  - ability to visualize games locally (current implementation is crude, but better than nothing)
  - more logging and ability to play games step-by-step locally
