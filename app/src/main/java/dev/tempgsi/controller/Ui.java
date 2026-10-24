package dev.tempgsi.controller;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.view.Gravity;
import android.widget.*;

/** Small native UI kit; uses system fonts including their Cyrillic fallback. */
final class Ui {
    static int BG=Color.rgb(16,20,18),SURFACE=Color.rgb(27,33,29),TEXT=Color.rgb(231,237,232),MUTED=Color.rgb(184,196,186),ACCENT=Color.rgb(159,215,173);
    private static int color(Activity a,String name,int fallback){int id=a.getResources().getIdentifier(name,"color","android");return id==0?fallback:a.getColor(id);}
    static int dp(android.content.Context c,float value){return Math.round(c.getResources().getDisplayMetrics().density*value);}
    static GradientDrawable shape(android.content.Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static LinearLayout screen(Activity a){
        BG=Color.rgb(12,14,20);SURFACE=Color.rgb(27,30,40);TEXT=Color.WHITE;MUTED=Color.rgb(193,199,214);ACCENT=color(a,"system_accent1_200",ACCENT);
        a.getWindow().setStatusBarColor(BG);a.getWindow().setNavigationBarColor(BG);
        a.getWindow().getDecorView().setSystemUiVisibility(0);
        ScrollView scroll=new ScrollView(a);scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(28,33,48),BG,Color.rgb(18,20,29)}));scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.setClipToPadding(false);scroll.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        LinearLayout body=new LinearLayout(a);body.setOrientation(1);body.setGravity(Gravity.CENTER_VERTICAL);body.setPadding(dp(a,24),dp(a,28),dp(a,24),dp(a,28));
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));a.setContentView(scroll);return body;
    }
    static TextView text(LinearLayout parent,String value,int sp,int color){
        TextView t=new TextView(parent.getContext());t.setText(value);t.setTextSize(sp);t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));t.setLineSpacing(dp(parent.getContext(),3),1);
        parent.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;
    }
    static TextView heading(LinearLayout p,String value){TextView t=text(p,value,30,TEXT);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    static void space(LinearLayout p,int height){Space s=new Space(p.getContext());p.addView(s,new LinearLayout.LayoutParams(1,dp(p.getContext(),height)));}
    static LinearLayout card(LinearLayout parent){
        LinearLayout box=new LinearLayout(parent.getContext());box.setOrientation(1);int pad=dp(parent.getContext(),18);box.setPadding(pad,pad,pad,pad);
        GradientDrawable surface=shape(parent.getContext(),SURFACE,32);surface.setStroke(dp(parent.getContext(),1),0x20ffffff);box.setBackground(surface);box.setElevation(dp(parent.getContext(),2));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(parent.getContext(),10);parent.addView(box,lp);return box;
    }
    static Button button(LinearLayout parent,String label,boolean primary,Runnable action){
        Button b=new Button(parent.getContext());b.setText(label);b.setAllCaps(false);b.setTextSize(16);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setTextColor(TEXT);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(parent.getContext(),primary?72:62));
        b.setPadding(dp(parent.getContext(),14),dp(parent.getContext(),12),dp(parent.getContext(),14),dp(parent.getContext(),12));
        GradientDrawable fill=shape(parent.getContext(),primary?Color.rgb(69,87,139):SURFACE,32);
        if(primary){fill.setColors(new int[]{Color.rgb(80,102,157),Color.rgb(55,71,119)});fill.setOrientation(GradientDrawable.Orientation.TL_BR);}
        fill.setStroke(dp(parent.getContext(),1),primary?0x608ea9ed:0x24ffffff);
        b.setBackgroundTintList(null);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),fill,shape(parent.getContext(),Color.WHITE,32)));
        b.setStateListAnimator(null);b.setElevation(dp(parent.getContext(),primary?5:1));
        b.setOnTouchListener((v,event)->{if(ValueAnimator.areAnimatorsEnabled()&&v.isEnabled()){
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN)v.animate().scaleX(.975f).scaleY(.975f).setDuration(110).start();
            else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)v.animate().scaleX(1).scaleY(1).setDuration(230).setInterpolator(new PathInterpolator(.2f,0,0,1)).start();
        }return false;});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(parent.getContext(),primary?72:62));lp.topMargin=dp(parent.getContext(),12);parent.addView(b,lp);b.setOnClickListener(v->action.run());return b;
    }
    static void reveal(LinearLayout page){
        if(!ValueAnimator.areAnimatorsEnabled())return;
        int order=0;
        for(int i=0;i<page.getChildCount();i++){View child=page.getChildAt(i);if(child instanceof Space)continue;
            child.setAlpha(0);child.setTranslationY(dp(page.getContext(),16));
            child.animate().alpha(1).translationY(0).setStartDelay(45L*order++).setDuration(450).setInterpolator(new PathInterpolator(.2f,0,0,1)).start();
        }
    }
    private Ui(){}
}
