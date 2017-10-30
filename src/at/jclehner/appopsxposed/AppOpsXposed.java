/*
 * AppOpsXposed - AppOps for Android 4.3+
 * Copyright (C) 2013-2015 Joseph C. Lehner
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of  MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package at.jclehner.appopsxposed;

import android.app.AndroidAppHelper;
import android.content.res.XModuleResources;

import at.jclehner.appopsxposed.util.Constants;
import at.jclehner.appopsxposed.util.Res;
import at.jclehner.appopsxposed.util.Util;
import at.jclehner.appopsxposed.util.XUtils;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static at.jclehner.appopsxposed.util.Util.log;

public class AppOpsXposed implements IXposedHookZygoteInit, IXposedHookLoadPackage, IXposedHookInitPackageResources {
    public static final String MODULE_PACKAGE = AppOpsXposed.class.getPackage().getName();
    public static final String SETTINGS_PACKAGE = "com.android.settings";
    public static final String SETTINGS_MAIN_ACTIVITY = SETTINGS_PACKAGE + ".Settings";
    public static final String APP_OPS_FRAGMENT = "com.android.settings.applications.AppOpsSummary";
    static final String APP_OPS_DETAILS_FRAGMENT = "com.android.settings.applications.AppOpsDetails";

    static {
        Util.logger = new Util.Logger() {

            @Override
            public void log(Throwable t) {
                XposedBridge.log(t);
            }

            @Override
            public void log(String s) {
                XposedBridge.log(s);
            }
        };
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        Res.modRes = XModuleResources.createInstance(startupParam.modulePath, null);
        Res.modPrefs = new XSharedPreferences(AppOpsXposed.class.getPackage().getName());
        Res.modPrefs.makeWorldReadable();
    }

    @Override
    public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable {
        if (!ApkVariant.isSettingsPackage(resparam.packageName))
            return;

        for (int i = 0; i != Res.icons.length; ++i)
            Res.icons[i] = resparam.res.addResource(Res.modRes, Constants.ICONS[i]);

        XUtils.reloadPrefs();

        for (ApkVariant variant : ApkVariant.getAllMatching(resparam.packageName)) {
            try {
                variant.handleInitPackageResources(resparam);
                break;
            } catch (Throwable t) {
                log(variant.getClass().getSimpleName() + ": [!!]");
                Util.debug(t);
            }
        }

        for (Hack hack : Hack.getAllEnabled(true)) {
            try {
                hack.handleInitPackageResources(resparam);
            } catch (Throwable t) {
                log(hack.getClass().getSimpleName() + ": [!!]");
                Util.debug(t);
            }
        }
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        final boolean isSettings = ApkVariant.isSettingsPackage(lpparam);

        if (MODULE_PACKAGE.equals(lpparam.packageName)) {
            XposedHelpers.findAndHookMethod(Util.class.getName(), lpparam.classLoader,
                    "isXposedModuleEnabled", XC_MethodReplacement.returnConstant(true));
        }

        XUtils.reloadPrefs();

        for (Hack hack : Hack.getAllEnabled(true)) {
            try {
                hack.handleLoadPackage(lpparam);
            } catch (Throwable t) {
                log(hack.getClass().getSimpleName() + ": [!!]");
                Util.debug(t);
            }
        }

        if (!isSettings)
            return;

        Class<?> instrumentation = XposedHelpers.findClass("android.app.Instrumentation", lpparam.classLoader);
        XposedBridge.hookAllMethods(instrumentation, "newActivity", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (Res.settingsRes == null) {
                    Res.settingsRes = AndroidAppHelper.currentApplication().getResources();
                }
            }
        });

        for (ApkVariant variant : ApkVariant.getAllMatching(lpparam)) {
            final String variantName = "  " + variant.getClass().getSimpleName();

            try {
                variant.handleLoadPackage(lpparam);
                log(variantName + ": [OK]");
                break;
            } catch (Throwable t) {
                Util.debug(variantName + ": [!!]");
                Util.debug(t);
            }
        }
    }
}
