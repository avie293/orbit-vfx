package cc.avie.orbitvfx.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/**
 * A fixed list of vanilla particle sprites, looked up once and refreshed after resource reloads.
 */
final class SpriteSet {
    private final Identifier[] ids;
    private final TextureAtlasSprite[] sprites;

    SpriteSet(String... names) {
        this.ids = new Identifier[names.length];
        for (int i = 0; i < names.length; i++) {
            this.ids[i] = Identifier.withDefaultNamespace(names[i]);
        }
        this.sprites = new TextureAtlasSprite[names.length];
    }

    TextureAtlasSprite[] all() {
        TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES);
        TextureAtlasSprite first = atlas.getSprite(this.ids[0]);
        // Reloads replace the sprites, so a changed first sprite means the whole list is stale.
        if (this.sprites[0] != first) {
            for (int i = 0; i < this.ids.length; i++) {
                this.sprites[i] = atlas.getSprite(this.ids[i]);
            }
        }
        return this.sprites;
    }

    TextureAtlasSprite random(RandomSource random) {
        TextureAtlasSprite[] all = this.all();
        return all[random.nextInt(all.length)];
    }
}
