package mezz.jei.common.platform;

public interface IPlatformModHelper {
	String getModNameForModId(String modId);

	boolean isModLoaded(String modId);

	boolean isInDev();
}
