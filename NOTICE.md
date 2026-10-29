# Notice

Buildcraft Reforged is a port of **BuildCraft 4.2.2** (Minecraft 1.6.4, released 4 January 2014) to NeoForge on
Minecraft 26.x.

BuildCraft was created by SpaceToad and developed by CovertJaguar, Krapht, SirSengir and the BuildCraft team.
Upstream: <https://github.com/BuildCraft/BuildCraft>, tag `4.2.2`.

The original code and assets are distributed under the Minecraft Mod Public License 1.0.1, included as `LICENSE`.
Buildcraft Reforged, including its changes and new code and art, is distributed under the same licence. The complete
source is available at <https://github.com/Kvaksrud/Buildcraft-Reforged>.

## What changed

- Platform: NeoForge 26.x instead of Forge for Minecraft 1.6.4.
- Energy: Forge Energy instead of MJ, at 1 MJ = 10 FE.
- Transport: a redesigned, rate-based pipe system with material tiers and modules.
- Engines: an overheat timer with an alarm; explosions are configurable.
- A status panel on every working block, an owner-profile fake player for block-breaking machines, and integrations
  (recipe viewer, wrench tags, comparator output, waterlogging).
- The version check that contacted the network on startup is removed; the mod makes no network connections.

Ported files keep their original copyright header, with a line naming the modification added.
