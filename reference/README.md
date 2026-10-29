# BuildCraft 4.2.2 reference sources

Read-only copy of BuildCraft at tag `4.2.2` (commit `1a1d2707fc6f22b7c39c1b8d02e52f09842c2b78`, 4 January 2014),
from <https://github.com/BuildCraft/BuildCraft>. It is here for diffing ported classes against their originals
(`docs/PLAN.md` section 6). It is never compiled and is not part of the jar. Do not edit it.

Contents: `common/`, `buildcraft_resources/`, `build.xml` and `README.md` from the tag, unchanged except for one thing:

## Normalised headers in 29 files

At tag 4.2.2, 29 files still carried an early "BuildCraft Open Source License" header, while the rest of the code
carried the Minecraft Mod Public License header and the release shipped `buildcraft_resources/LICENSE.txt`
(MMPL 1.0.1). SpaceToad moved the codebase to MMPL in November 2011 (`dcf49f49e`) and later replaced the old header in
exactly these 29 files with the MMPL header in `360d782ea` ("normalized file headers", 15 February 2014, first
released in 5.0.0). The copies here carry that normalised header, byte for byte as upstream wrote it; the code
below the header is the 4.2.2 code. See issue #1 for the history.

The 29 files:

```
common/buildcraft/BuildCraftCore.java
common/buildcraft/BuildCraftEnergy.java
common/buildcraft/BuildCraftFactory.java
common/buildcraft/BuildCraftSilicon.java
common/buildcraft/BuildCraftTransport.java
common/buildcraft/core/InterModComms.java
common/buildcraft/core/utils/BlockUtil.java
common/buildcraft/factory/BptBlockRefinery.java
common/buildcraft/transport/pipes/PipeFluidsCobblestone.java
common/buildcraft/transport/pipes/PipeFluidsGold.java
common/buildcraft/transport/pipes/PipeFluidsIron.java
common/buildcraft/transport/pipes/PipeFluidsStone.java
common/buildcraft/transport/pipes/PipeFluidsWood.java
common/buildcraft/transport/pipes/PipeItemsCobblestone.java
common/buildcraft/transport/pipes/PipeItemsDaizuli.java
common/buildcraft/transport/pipes/PipeItemsDiamond.java
common/buildcraft/transport/pipes/PipeItemsGold.java
common/buildcraft/transport/pipes/PipeItemsIron.java
common/buildcraft/transport/pipes/PipeItemsObsidian.java
common/buildcraft/transport/pipes/PipeItemsQuartz.java
common/buildcraft/transport/pipes/PipeItemsStone.java
common/buildcraft/transport/pipes/PipeItemsWood.java
common/buildcraft/transport/pipes/PipePowerCobblestone.java
common/buildcraft/transport/pipes/PipePowerDiamond.java
common/buildcraft/transport/pipes/PipePowerGold.java
common/buildcraft/transport/pipes/PipePowerIron.java
common/buildcraft/transport/pipes/PipePowerQuartz.java
common/buildcraft/transport/pipes/PipePowerStone.java
common/buildcraft/transport/pipes/PipePowerWood.java
```
