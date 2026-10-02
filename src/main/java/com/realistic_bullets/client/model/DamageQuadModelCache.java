package com.realistic_bullets.client.model;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import com.realistic_bullets.voxel.VoxelVolume;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Кэш скомпилированных моделей: поверхность оригинального блока разбивается
 * на ячейки 16³ (целые — рисуем, выбитые — пропуск) + тёмные стенки выбоин.
 */
public final class DamageQuadModelCache {
    private DamageQuadModelCache() {}

    private static final int MAX_ENTRIES = 256;
    private static final Map<ModelCacheKey, CompiledModel> CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ModelCacheKey, CompiledModel> e) {
                    return size() > MAX_ENTRIES;
                }
            });

    // ---- layout вершин BLOCK-формата (1.21.1: 8 интов на вершину) ----
    private static final int STRIDE;
    private static final int OFF_POS, OFF_COLOR, OFF_UV0, OFF_NORMAL;
    private static final List<Integer> SHORT_UV_OFFSETS = new ArrayList<>();

    static {
        VertexFormat f = DefaultVertexFormat.BLOCK;
        STRIDE = f.getVertexSize() / 4;
        int pos = 0, color = 0, uv0 = 0, normal = 0, off = 0;
        boolean uv0Found = false;
        for (VertexFormatElement el : f.getElements()) {
            switch (el.getUsage()) {
                case POSITION -> pos = off;
                case COLOR -> color = off;
                case UV -> {
                    if (!uv0Found && el.getType() == VertexFormatElement.Type.FLOAT) { uv0 = off; uv0Found = true; }
                    else SHORT_UV_OFFSETS.add(off);
                }
                case NORMAL -> normal = off;
                default -> { }
            }
            off += el.getByteSize() / 4;
        }
        OFF_POS = pos; OFF_COLOR = color; OFF_UV0 = uv0; OFF_NORMAL = normal;
    }

    public static final class CompiledModel {
        final Map<Direction, List<BakedQuad>> bySide = new EnumMap<>(Direction.class);
        List<BakedQuad> general = List.of();
    }

    private static final class ModelCacheKey {
        final BlockState state; final long[] voxels;
        ModelCacheKey(BlockState s, long[] v) { state = s; voxels = v; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ModelCacheKey k)) return false;
            return state.equals(k.state) && Arrays.equals(voxels, k.voxels);
        }
        @Override public int hashCode() { return 31 * state.hashCode() + Arrays.hashCode(voxels); }
    }

    public static CompiledModel get(BlockState original, VoxelVolume volume) {
        long[] vox = volume.toLongArray();
        ModelCacheKey key = new ModelCacheKey(original, vox);
        CompiledModel m = CACHE.get(key);
        if (m == null) {
            m = compile(original, vox);
            CACHE.put(key, m);
        }
        return m;
    }

    private static CompiledModel compile(BlockState state, long[] vox) {
        CompiledModel out = new CompiledModel();
        BakedModel model = Minecraft.getInstance().getBlockRenderer()
                .getBlockModelShaper().getBlockModel(state);
        RandomSource rand = RandomSource.create(42);
        for (Direction side : Direction.values()) {
            out.bySide.put(side, rebuildSide(model.getQuads(state, side, rand), side, vox));
        }
        out.general = model.getQuads(state, null, rand); // у кубов пусто
        addCraterWalls(out, state, vox, model);
        return out;
    }

    // ---------- поверхность ----------

    private static List<BakedQuad> rebuildSide(List<BakedQuad> quads, Direction side, long[] vox) {
        if (quads.isEmpty()) return quads;
        List<BakedQuad> out = new ArrayList<>(quads.size() + 32);
        for (BakedQuad q : quads) {
            FacePlane p = FacePlane.of(q, side);
            if (p == null || p.fullyIntact(vox)) { out.add(q); continue; }
            for (int a = p.a0; a < p.a1; a++) {
                for (int b = p.b0; b < p.b1; b++) {
                    if (damagedCell(vox, side, a, b)) continue; // дыра
                    out.add(p.subQuad(q, a, b));
                }
            }
        }
        return out;
    }

    /** Ячейка на грани side с координатами (a,b) по двум осям плоскости. */
    private static boolean damagedCell(long[] vox, Direction side, int a, int b) {
        return switch (side) {
            case UP -> isDmg(vox, a, 15, b);
            case DOWN -> isDmg(vox, a, 0, b);
            case NORTH -> isDmg(vox, a, b, 0);
            case SOUTH -> isDmg(vox, a, b, 15);
            case WEST -> isDmg(vox, 0, b, a);
            case EAST -> isDmg(vox, 15, b, a);
        };
    }

    private static boolean isDmg(long[] vox, int x, int y, int z) {
        int i = x + (z << 4) + (y << 8);
        return (vox[i >>> 6] >>> (i & 63) & 1L) != 0L;
    }

    /** Геометрия квада на грани: билинейная параметризация + привязка к ячейкам. */
    private static final class FacePlane {
        Direction side;
        int aAxis, bAxis;           // оси плоскости (0=x,1=y,2=z)
        float fa0, fa1, fb0, fb1;   // экстенты квада
        float[] p0 = new float[3];  // вершина 0
        float[] eS = new float[3];  // 0 -> 1
        float[] eT = new float[3];  // 0 -> 3
        float[] uv = new float[8];  // u0..u3, v0..v3
        int a0, a1, b0, b1;         // ячейки

        static FacePlane of(BakedQuad q, Direction side) {
            int[] d = q.getVertices();
            float[][] p = new float[4][3];
            for (int i = 0; i < 4; i++) {
                int base = i * STRIDE + OFF_POS;
                p[i][0] = Float.intBitsToFloat(d[base]);
                p[i][1] = Float.intBitsToFloat(d[base + 1]);
                p[i][2] = Float.intBitsToFloat(d[base + 2]);
            }
            int fixed = -1;
            for (int axis = 0; axis < 3; axis++) {
                boolean same = true;
                for (int i = 1; i < 4; i++) if (Math.abs(p[i][axis] - p[0][axis]) > 1e-4f) same = false;
                if (same) fixed = axis;
            }
            if (fixed < 0) return null; // не плоский квад — не трогаем
            FacePlane fp = new FacePlane();
            fp.side = side;
            fp.aAxis = (fixed + 1) % 3;
            fp.bAxis = (fixed + 2) % 3;
            System.arraycopy(p[0], 0, fp.p0, 0, 3);
            for (int k = 0; k < 3; k++) {
                fp.eS[k] = p[1][k] - p[0][k];
                fp.eT[k] = p[3][k] - p[0][k];
            }
            fp.fa0 = min4(p, fp.aAxis); fp.fa1 = max4(p, fp.aAxis);
            fp.fb0 = min4(p, fp.bAxis); fp.fb1 = max4(p, fp.bAxis);
            for (int i = 0; i < 4; i++) {
                int base = i * STRIDE + OFF_UV0;
                fp.uv[i] = Float.intBitsToFloat(d[base]);
                fp.uv[i + 4] = Float.intBitsToFloat(d[base + 1]);
            }
            fp.a0 = clampCell((int) Math.floor(fp.fa0));
            fp.a1 = clampCell((int) Math.ceil(fp.fa1));
            fp.b0 = clampCell((int) Math.floor(fp.fb0));
            fp.b1 = clampCell((int) Math.ceil(fp.fb1));
            return fp;
        }

        boolean fullyIntact(long[] vox) {
            for (int a = a0; a < a1; a++)
                for (int b = b0; b < b1; b++)
                    if (damagedCell(vox, side, a, b)) return false;
            return true;
        }

        /** Квад ячейки (a,b) с UV, билинейно унаследованными от оригинала. */
        BakedQuad subQuad(BakedQuad src, int a, int b) {
            float s0 = clamp01((a - fa0) / (fa1 - fa0)), s1 = clamp01((a + 1 - fa0) / (fa1 - fa0));
            float t0 = clamp01((b - fb0) / (fb1 - fb0)), t1 = clamp01((b + 1 - fb0) / (fb1 - fb0));
            float[][] pos = new float[4][3];
            float[] u = new float[4], v = new float[4];
            float[][] st = {{s0, t0}, {s1, t0}, {s1, t1}, {s0, t1}};
            for (int i = 0; i < 4; i++) {
                float s = st[i][0], t = st[i][1];
                for (int k = 0; k < 3; k++) pos[i][k] = p0[k] + eS[k] * s + eT[k] * t;
                u[i] = bilerp(uv, s, t, 0);
                v[i] = bilerp(uv, s, t, 4);
            }
            return buildQuad(pos, src.getDirection(), src.getSprite(), u, v,
                    src.getTintIndex(), src.isShade(), 0xFFFFFFFF);
        }

        private static float bilerp(float[] uv, float s, float t, int o) {
            return (1 - s) * (1 - t) * uv[o] + s * (1 - t) * uv[o + 1]
                    + s * t * uv[o + 2] + (1 - s) * t * uv[o + 3];
        }
    }

    // ---------- стенки кратеров ----------

    private static void addCraterWalls(CompiledModel out, BlockState state, long[] vox, BakedModel model) {
        TextureAtlasSprite[] faceSprites = new TextureAtlasSprite[6];
        RandomSource rand = RandomSource.create(42);
        for (Direction d : Direction.values()) {
            List<BakedQuad> qs = model.getQuads(state, d, rand);
            if (!qs.isEmpty()) faceSprites[d.ordinal()] = qs.get(0).getSprite();
        }
        TextureAtlasSprite fallback = model.getParticleIcon();

        for (int x = 0; x < VoxelVolume.SIZE; x++)
            for (int y = 0; y < VoxelVolume.SIZE; y++)
                for (int z = 0; z < VoxelVolume.SIZE; z++) {
                    if (!isDmg(vox, x, y, z)) continue;
                    for (Direction d : Direction.values()) {
                        int nx = x + d.getStepX(), ny = y + d.getStepY(), nz = z + d.getStepZ();
                        if (nx < 0 || ny < 0 || nz < 0 || nx > 15 || ny > 15 || nz > 15) continue;
                        if (isDmg(vox, nx, ny, nz)) continue;
                        // стенка = грань целого соседа, обращённая в дыру
                        Direction normal = d.getOpposite();
                        TextureAtlasSprite sprite = faceSprites[normal.ordinal()];
                        if (sprite == null) sprite = fallback;
                        BakedQuad wall = wallQuad(x, y, z, d, normal, sprite, hash(x, y, z));
                        out.bySide.get(normal).add(wall);
                    }
                }
    }

    private static int hash(int x, int y, int z) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        return (h ^ (h >>> 13)) & 0x3F; // 0..63
    }

    private static BakedQuad wallQuad(int x, int y, int z, Direction toNeighbor,
                                      Direction normal, TextureAtlasSprite sprite, int noise) {
        int axis = normal.getAxis().ordinal();          // ось нормали
        int aAxis = (axis + 1) % 3, bAxis = (axis + 2) % 3;
        int[] cell = {x, y, z};
        // граница между дырой и соседом: если сосед лежит вдоль нормали —
        // плоскость на его грани, иначе на грани дыры по оси нормали
        float fixed;
        if (toNeighbor.getAxis() == normal.getAxis()) {
            fixed = normal.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? cell[axis] + 1 : cell[axis];
        } else {
            fixed = toNeighbor.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? cell[axis] + 1 : cell[axis];
        }
        float a0 = cell[aAxis], a1 = a0 + 1, b0 = cell[bAxis], b1 = b0 + 1;
        float[][] corners = new float[4][3];
        float[][] st = {{a0, b0}, {a1, b0}, {a1, b1}, {a0, b1}};
        for (int i = 0; i < 4; i++) {
            corners[i][axis] = fixed;
            corners[i][aAxis] = st[i][0];
            corners[i][bAxis] = st[i][1];
        }
        fixWinding(corners, normal);
        float[] u = new float[4], v = new float[4];
        for (int i = 0; i < 4; i++) {
            u[i] = sprite.getU(st[i][0] / 16f);
            v[i] = sprite.getV(st[i][1] / 16f);
        }
        int g = 0x4A + (noise >> 2); // тёмные стенки с лёгким шумом
        int argb = 0xFF000000 | (g << 16) | (g << 8) | g;
        return buildQuad(corners, normal, sprite, u, v, -1, true, argb);
    }

    /** Порядок 0,1,2,3 должен давать CCW при взгляде вдоль нормали. */
    private static void fixWinding(float[][] p, Direction normal) {
        float[] e1 = {p[1][0] - p[0][0], p[1][1] - p[0][1], p[1][2] - p[0][2]};
        float[] e2 = {p[3][0] - p[0][0], p[3][1] - p[0][1], p[3][2] - p[0][2]};
        float cx = e1[1] * e2[2] - e1[2] * e2[1];
        float cy = e1[2] * e2[0] - e1[0] * e2[2];
        float cz = e1[0] * e2[1] - e1[1] * e2[0];
        Vec3i n = normal.getNormal();
        if (cx * n.getX() + cy * n.getY() + cz * n.getZ() < 0) {
            float[] tmp = p[1]; p[1] = p[3]; p[3] = tmp;
        }
    }

    // ---------- сборка вершин ----------

    private static BakedQuad buildQuad(float[][] pos, Direction dir, TextureAtlasSprite sprite,
                                       float[] u, float[] v, int tint, boolean shade, int argb) {
        int[] data = new int[STRIDE * 4];
        Vec3i n = dir.getNormal();
        int packedNormal = (n.getX() & 255) | ((n.getY() & 255) << 8) | ((n.getZ() & 255) << 16);
        for (int i = 0; i < 4; i++) {
            int base = i * STRIDE;
            data[base + OFF_POS] = Float.floatToRawIntBits(pos[i][0]);
            data[base + OFF_POS + 1] = Float.floatToRawIntBits(pos[i][1]);
            data[base + OFF_POS + 2] = Float.floatToRawIntBits(pos[i][2]);
            data[base + OFF_COLOR] = argb;
            data[base + OFF_UV0] = Float.floatToRawIntBits(u[i]);
            data[base + OFF_UV0 + 1] = Float.floatToRawIntBits(v[i]);
            for (int o : SHORT_UV_OFFSETS) data[base + o] = 0;
            data[base + OFF_NORMAL] = packedNormal;
        }
        // 1.21.1: (int[] vertices, int tintIndex, Direction, TextureAtlasSprite, boolean shade, int lightEmission)
        return new BakedQuad(data, tint, dir, sprite, shade, 0);
    }

    private static int clampCell(int v) { return Math.max(0, Math.min(16, v)); }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float min4(float[][] p, int axis) {
        float m = p[0][axis]; for (int i = 1; i < 4; i++) m = Math.min(m, p[i][axis]); return m; }
    private static float max4(float[][] p, int axis) {
        float m = p[0][axis]; for (int i = 1; i < 4; i++) m = Math.max(m, p[i][axis]); return m; }
}
