package raccoonman.reterraforged.common.level.levelgen.test.world.continent.advanced;

import raccoonman.reterraforged.common.level.levelgen.noise.NoiseUtil;
import raccoonman.reterraforged.common.level.levelgen.noise.Seed;
import raccoonman.reterraforged.common.level.levelgen.test.world.GeneratorContext;
import raccoonman.reterraforged.common.level.levelgen.test.world.continent.SimpleContinent;
import raccoonman.reterraforged.common.level.levelgen.test.world.continent.simple.SimpleRiverGenerator;
import raccoonman.reterraforged.common.level.levelgen.test.world.heightmap.ControlPoints;
import raccoonman.reterraforged.common.level.levelgen.test.world.rivermap.RiverCache;
import raccoonman.reterraforged.common.worldgen.data.preset.WorldSettings;

public abstract class AbstractContinent implements SimpleContinent {
    protected int seed;
    protected int skippingSeed;
    protected int continentScale;
    protected float jitter;
    protected boolean hasSkipping;
    protected float skipThreshold;
    protected RiverCache riverCache;
    protected ControlPoints controlPoints;
    
    public AbstractContinent(Seed seed, GeneratorContext context) {
        WorldSettings settings = context.settings.world();
        this.seed = seed.next();
        this.skippingSeed = seed.next();
        this.continentScale = settings.continent.continentScale;
        this.jitter = settings.continent.continentJitter;
        this.skipThreshold = settings.continent.continentSkipping;
        this.hasSkipping = (this.skipThreshold > 0.0F);
        this.controlPoints = ControlPoints.make(settings.controlPoints);
        this.riverCache = new RiverCache(new SimpleRiverGenerator(this, context));
    }
    
    @Override
    public float getDistanceToOcean(int cx, int cz, float dx, float dz, int seed) {
        float high = this.getDistanceToEdge(cx, cz, dx, dz, seed);
        float low = 0.0F;
        for (int i = 0; i < 50; ++i) {
            float mid = (low + high) / 2.0F;
            float x = cx + dx * mid;
            float z = cz + dz * mid;
            float edge = this.getEdgeValue(x, z, seed);
            if (edge > this.controlPoints.shallowOcean()) {
                low = mid;
            }
            else {
                high = mid;
            }
            if (high - low < 10.0F) {
                break;
            }
        }
        return high;
    }
    
    @Override
    public float getDistanceToEdge(int cx, int cz, float dx, float dz, int seed) {
        float distance = (float)(this.continentScale * 4);
        for (int i = 0; i < 10; ++i) {
            float x = cx + dx * distance;
            float z = cz + dz * distance;
            long centerPos = this.getNearestCenter(x, z, seed);
            int conX = NoiseUtil.unpackLeft(centerPos);
            int conZ = NoiseUtil.unpackRight(centerPos);
            distance += distance;
            if (conX != cx || conZ != cz) {
                float low = 0.0f;
                float high = distance;
                for (int j = 0; j < 50; ++j) {
                    float mid = (low + high) / 2.0F;
                    float px = cx + dx * mid;
                    float pz = cz + dz * mid;
                    centerPos = this.getNearestCenter(px, pz, seed);
                    conX = NoiseUtil.unpackLeft(centerPos);
                    conZ = NoiseUtil.unpackRight(centerPos);
                    if (conX == cx && conZ == cz) {
                        low = mid;
                    }
                    else {
                        high = mid;
                    }
                    if (high - low < 50.0F) {
                        break;
                    }
                }
                return high;
            }
        }
        return distance;
    }
    
    protected boolean isDefaultContinent(int cellX, int cellY) {
        return cellX == 0 && cellY == 0;
    }
    
    protected boolean shouldSkip(int cellX, int cellY) {
        if (this.hasSkipping && !this.isDefaultContinent(cellX, cellY)) {
            float skipValue = getCellValue(this.skippingSeed, cellX, cellY);
            return skipValue < this.skipThreshold;
        }
        return false;
    }
    
    protected static float getCellValue(int seed, int cellX, int cellY) {
        return 0.5F + NoiseUtil.valCoord2D(seed, cellX, cellY) * 0.5F;
    }
}
