/*
 * Ported from buildcraft/core/utils/SessionVars.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: package name and generics only.
 */
package buildcraftreforged.core.utils;

public class SessionVars {

    private static Class<?> openedLedger;

    public static void setOpenedLedger(Class<?> ledgerClass) {
        openedLedger = ledgerClass;
    }

    public static Class<?> getOpenedLedger() {
        return openedLedger;
    }
}
