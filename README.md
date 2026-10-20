# OblivionFishingPremium

Premium-style custom fishing plugin for Oblivion SMP. Targets Paper 1.21.4 and Java 21.

## Included
- Six loot rarities: Common, Uncommon, Rare, Epic, Legendary, Mythic.
- Custom item names, lore, amounts, per-item weights, and rarity weights configured in `config.yml`.
- Dynamic relative rarity modifiers for night, rain, and special biomes.
- Pity weighting that increases rare-tier odds after dry streaks (not a guaranteed drop).
- Fishing XP, basic level calculation, rare catch broadcast, catch stats, and a discovery journal GUI.
- `/fishing` or `/fish`: journal; `/fishing stats`: profile; `/fishing reload`: reload config (OP).

## Build
Requires Maven and Java 21. Run `mvn package`, then copy `target/OblivionFishingPremium-1.0.0.jar` to your server's `plugins/` folder and restart.

## Honest limitations
This is a source project, not a precompiled JAR. It has not been compiled or tested in this environment. Player stats and journal discoveries are memory-only and reset on server restart. Database persistence, custom bait, sell shop, tournaments, and a real cross-player leaderboard are not implemented. Verify compatibility with your exact Paper version before using on production.
