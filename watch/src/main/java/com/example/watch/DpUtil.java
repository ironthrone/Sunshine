package com.example.watch;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.TypedValue;


/**
 *
 */
public class DpUtil {

	private static  DisplayMetrics getDisplayMetrics(Context context){
		return context.getResources().getDisplayMetrics();
	}
	public static float px2dp(Context context,float pxValue) {
		DisplayMetrics metrics = getDisplayMetrics(context);
		return pxValue / metrics.density;

	}

	public static float dp2px(Context context,float dpValue) {
		return   TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,dpValue,getDisplayMetrics(context));
	}


	public static float px2sp(Context context,float pxValue) {
		DisplayMetrics metrics = getDisplayMetrics(context);
		return pxValue / metrics.scaledDensity;

	}


	public static float sp2px(Context context,float spValue) {
		return   TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,spValue,getDisplayMetrics(context));
	}

}
