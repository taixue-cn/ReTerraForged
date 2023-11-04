package raccoonman.reterraforged.common.level.levelgen.test.world.rivermap.river;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import raccoonman.reterraforged.common.level.levelgen.noise.NoiseUtil;
import raccoonman.reterraforged.common.level.levelgen.noise.Vec2f;
import raccoonman.reterraforged.common.level.levelgen.test.util.Variance;
import raccoonman.reterraforged.common.level.levelgen.test.world.GeneratorContext;
import raccoonman.reterraforged.common.level.levelgen.test.world.continent.Continent;
import raccoonman.reterraforged.common.level.levelgen.test.world.heightmap.Levels;
import raccoonman.reterraforged.common.level.levelgen.test.world.rivermap.RiverGenerator;
import raccoonman.reterraforged.common.level.levelgen.test.world.rivermap.Rivermap;
import raccoonman.reterraforged.common.level.levelgen.test.world.rivermap.gen.GenWarp;
import raccoonman.reterraforged.common.level.levelgen.test.world.rivermap.lake.Lake;
import raccoonman.reterraforged.common.level.levelgen.test.world.rivermap.lake.LakeConfig;
import raccoonman.reterraforged.common.level.levelgen.test.world.rivermap.wetland.Wetland;
import raccoonman.reterraforged.common.level.levelgen.test.world.rivermap.wetland.WetlandConfig;

public abstract class BaseRiverGenerator<T extends Continent> implements RiverGenerator {
    protected int count;
    protected int continentScale;
    protected float minEdgeValue;
    protected int seed;
    protected LakeConfig lake;
    protected RiverConfig main;
    protected RiverConfig fork;
    protected WetlandConfig wetland;
    protected T continent;
    protected Levels levels;
    
    public BaseRiverGenerator(T continent, GeneratorContext context) {
        this.continent = continent;
        this.levels = context.levels;
        this.continentScale = context.settings.world().continent.continentScale;
        this.minEdgeValue = context.settings.world().controlPoints.inland;
        this.seed = context.seed.root() + context.settings.rivers().seedOffset;
        this.count = context.settings.rivers().riverCount;
        this.main = RiverConfig.builder(context.levels).bankHeight(context.settings.rivers().mainRivers.minBankHeight, context.settings.rivers().mainRivers.maxBankHeight).bankWidth(context.settings.rivers().mainRivers.bankWidth).bedWidth(context.settings.rivers().mainRivers.bedWidth).bedDepth(context.settings.rivers().mainRivers.bedDepth).fade(context.settings.rivers().mainRivers.fade).length(5000).main(true).order(0).build();
        this.fork = RiverConfig.builder(context.levels).bankHeight(context.settings.rivers().branchRivers.minBankHeight, context.settings.rivers().branchRivers.maxBankHeight).bankWidth(context.settings.rivers().branchRivers.bankWidth).bedWidth(context.settings.rivers().branchRivers.bedWidth).bedDepth(context.settings.rivers().branchRivers.bedDepth).fade(context.settings.rivers().branchRivers.fade).length(4500).order(1).build();
        this.wetland = new WetlandConfig(context.settings.rivers().wetlands);
        this.lake = LakeConfig.of(context.settings.rivers().lakes, context.levels);
    }
    
    @Override
    public Rivermap generateRivers(int x, int z, long id, int seed) {
        Random random = new Random(id + this.seed);
        GenWarp warp = new GenWarp((int) id, this.continentScale);
        List<Network.Builder> rivers = this.generateRoots(x, z, random, warp, seed);
        Collections.shuffle(rivers, random);
        for (Network.Builder root : rivers) {
            this.generateForks(root, River.MAIN_SPACING, this.fork, random, warp, rivers, 0, seed);
        }
        for (Network.Builder river : rivers) {
            this.generateWetlands(river, random);
        }
        Network[] networks = rivers.stream().map(Network.Builder::build).toArray(Network[]::new);
        return new Rivermap(x, z, networks, warp);
    }
    
    public List<Network.Builder> generateRoots(int x, int z, Random random, GenWarp warp, int seed) {
        return Collections.emptyList();
    }
    
    public void generateForks(Network.Builder parent, Variance spacing, RiverConfig config, Random random, GenWarp warp, List<Network.Builder> rivers, int depth, int seed) {
        if (depth > 2) {
            return;
        }
        float length = 0.44f * parent.carver.river.length;
        if (length < 300.0f) {
            return;
        }
        int direction = random.nextBoolean() ? 1 : -1;
        for (float offset = 0.25f; offset < 0.9f; offset += spacing.next(random)) {
            for (boolean attempt = true; attempt; attempt = false) {
                direction = -direction;
                float parentAngle = parent.carver.river.getAngle();
                float forkAngle = direction * 6.2831855f * River.FORK_ANGLE.next(random);
                float angle = parentAngle + forkAngle;
                float dx = NoiseUtil.sin(angle);
                float dz = NoiseUtil.cos(angle);
                long v1 = parent.carver.river.pos(offset);
                float x1 = NoiseUtil.unpackLeftf(v1);
                float z1 = NoiseUtil.unpackRightf(v1);
                if (this.continent.getEdgeValue(x1, z1, seed) >= this.minEdgeValue) {
                    float x2 = x1 - dx * length;
                    float z2 = z1 - dz * length;
                    if (this.continent.getEdgeValue(x2, z2, seed) >= this.minEdgeValue) {
                        RiverConfig forkConfig = parent.carver.createForkConfig(offset, this.levels);
                        River river = new River(x2, z2, x1, z1);
                        if (!this.riverOverlaps(river, parent, rivers)) {
                            float valleyWidth = 275.0f * River.FORK_VALLEY.next(random);
                            RiverCarver.Settings settings = creatSettings(random);
                            settings.connecting = true;
                            settings.fadeIn = config.fade;
                            settings.valleySize = valleyWidth;
                            RiverWarp forkWarp = parent.carver.warp.createChild(0.15f, 0.75f, 0.65f, random);
                            RiverCarver fork = new RiverCarver(river, forkWarp, forkConfig, settings, this.levels);
                            Network.Builder builder = Network.builder(fork);
                            parent.children.add(builder);
                            this.generateForks(builder, River.FORK_SPACING, config, random, warp, rivers, depth + 1, seed);
                        }
                    }
                }
            }
        }
        this.addLake(parent, random, warp);
    }
    
    public void generateAdditionalLakes(int x, int z, Random random, List<Network.Builder> roots, List<RiverCarver> rivers, List<Lake> lakes) {
        float size = 150.0f;
        Variance sizeVariance = Variance.of(1.0F, 0.25F);
        Variance distanceVariance = Variance.of(0.6000000238418579F, 0.30000001192092896F);
        for (int i = 1; i < roots.size(); ++i) {
            Network.Builder a = roots.get(i - 1);
            float angle = 0.0F;
            float dx = NoiseUtil.sin(angle);
            float dz = NoiseUtil.cos(angle);
            float distance = distanceVariance.next(random);
            float lx = x + dx * a.carver.river.length * distance;
            float lz = z + dz * a.carver.river.length * distance;
            float variance = sizeVariance.next(random);
            Vec2f center = new Vec2f(lx, lz);
            if (!this.lakeOverlaps(center, size, rivers)) {
                lakes.add(new Lake(center, size, variance, this.lake));
            }
        }
    }
    
    public void generateWetlands(Network.Builder builder, Random random) {
        int skip = random.nextInt(this.wetland.skipSize);
        if (skip == 0) {
            float width = this.wetland.width.next(random);
            float length = this.wetland.length.next(random);
            float riverLength = builder.carver.river.length();
            float startPos = random.nextFloat() * 0.75f;
            float endPos = startPos + random.nextFloat() * (length / riverLength);
            long start = builder.carver.river.pos(startPos);
            long end = builder.carver.river.pos(endPos);
            float x1 = NoiseUtil.unpackLeftf(start);
            float z1 = NoiseUtil.unpackRightf(start);
            float x2 = NoiseUtil.unpackLeftf(end);
            float z2 = NoiseUtil.unpackRightf(end);
            builder.wetlands.add(new Wetland(random.nextInt(), new Vec2f(x1, z1), new Vec2f(x2, z2), width, this.levels));
        }
        for (Network.Builder child : builder.children) {
            this.generateWetlands(child, random);
        }
    }
    
    public void addLake(Network.Builder branch, Random random, GenWarp warp) {
        if (random.nextFloat() <= this.lake.chance) {
            float lakeSize = this.lake.sizeMin + random.nextFloat() * this.lake.sizeRange;
            float cx = branch.carver.river.x1;
            float cz = branch.carver.river.z1;
            if (this.lakeOverlapsOther(cx, cz, lakeSize, branch.lakes)) {
                return;
            }
            branch.lakes.add(new Lake(new Vec2f(cx, cz), lakeSize, 1.0f, this.lake));
        }
    }
    
    public boolean riverOverlaps(River river, Network.Builder parent, List<Network.Builder> rivers) {
        for (Network.Builder other : rivers) {
            if (other.overlaps(river, parent, 250.0f)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean lakeOverlaps(Vec2f lake, float size, List<RiverCarver> rivers) {
        for (RiverCarver other : rivers) {
            if (!other.main && other.river.overlaps(lake, size)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean lakeOverlapsOther(float x, float z, float size, List<Lake> lakes) {
        float dist2 = size * size;
        for (Lake other : lakes) {
            if (other.overlaps(x, z, dist2)) {
                return true;
            }
        }
        return false;
    }
    
    public static RiverCarver create(float x1, float z1, float x2, float z2, RiverConfig config, Levels levels, Random random) {
        River river = new River(x1, z1, x2, z2);
        RiverWarp warp = RiverWarp.create(0.35f, random);
        float valleyWidth = 275.0f * River.MAIN_VALLEY.next(random);
        RiverCarver.Settings settings = creatSettings(random);
        settings.connecting = false;
        settings.fadeIn = config.fade;
        settings.valleySize = valleyWidth;
        return new RiverCarver(river, warp, config, settings, levels);
    }
    
    public static RiverCarver createFork(float x1, float z1, float x2, float z2, float valleyWidth, RiverConfig config, Levels levels, Random random) {
        River river = new River(x1, z1, x2, z2);
        RiverWarp warp = RiverWarp.create(0.4f, random);
        RiverCarver.Settings settings = creatSettings(random);
        settings.connecting = true;
        settings.fadeIn = config.fade;
        settings.valleySize = valleyWidth;
        return new RiverCarver(river, warp, config, settings, levels);
    }
    
    public static RiverCarver.Settings creatSettings(Random random) {
        RiverCarver.Settings settings = new RiverCarver.Settings();
        settings.valleyCurve = RiverCarver.getValleyType(random);
        return settings;
    }
}
