-- Rareté imposée par l'administrateur : remplace le calcul (hors quotas), la carte est toujours dans le paquet.
ALTER TABLE anime_character ADD COLUMN forced_rarity VARCHAR(20);
