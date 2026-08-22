package com.saeid.mosquitosmash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class GameView extends View {
    private static final int NORMAL=0, GOLD=1, BOSS=2;
    private static final int BOMB=0, FREEZE=1;

    private final Random rnd = new Random();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Bug> bugs = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<FloatText> texts = new ArrayList<>();
    private final List<Power> powers = new ArrayList<>();
    private final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);

    private long lastFrame=SystemClock.uptimeMillis(), lastHit=0, lastPower=0;
    private long freezeUntil=0, flashUntil=0, shakeUntil=0, bannerUntil=0, bossBannerUntil=0;
    private int score=0, combo=0, level=1, nextBoss=15, flashColor=0x33FFFFFF;
    private boolean initialized=false, sound=true;

    public GameView(Context c) {
        super(c);
        setWillNotDraw(false);
        tp.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
    }

    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        super.onSizeChanged(w,h,ow,oh);
        if(!initialized && w>0 && h>0){
            initialized=true;
            for(int i=0;i<3;i++) bugs.add(makeBug(NORMAL));
            lastPower=SystemClock.uptimeMillis();
        }
    }

    private Bug makeBug(int requested){
        Bug b=new Bug();
        b.type=requested;
        if(requested==NORMAL && score>=5 && rnd.nextFloat()<.15f) b.type=GOLD;
        if(b.type==BOSS){ b.size=dp(66); b.hp=b.maxHp=3+Math.min(2,level/3); b.value=10; }
        else if(b.type==GOLD){ b.size=dp(30+rnd.nextInt(9)); b.hp=b.maxHp=1; b.value=5; }
        else { b.size=dp(34+rnd.nextInt(17)); b.hp=b.maxHp=1; b.value=1; }
        b.x=dp(65)+rnd.nextFloat()*Math.max(1,getWidth()-dp(130));
        b.y=dp(135)+rnd.nextFloat()*Math.max(1,getHeight()-dp(275));
        float sp=dp(105+Math.min(150,(level-1)*18)+rnd.nextInt(65));
        if(b.type==GOLD)sp*=1.45f; if(b.type==BOSS)sp*=.62f;
        float a=rnd.nextFloat()*(float)Math.PI*2;
        b.vx=(float)Math.cos(a)*sp; b.vy=(float)Math.sin(a)*sp; b.phase=rnd.nextFloat()*9;
        return b;
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        long now=SystemClock.uptimeMillis();
        float dt=Math.min(.035f,Math.max(.001f,(now-lastFrame)/1000f)); lastFrame=now;
        if(!initialized){ postInvalidateOnAnimation(); return; }
        spawnPower(now); update(dt,now);
        c.save();
        if(now<shakeUntil){ float s=dp(9)*(shakeUntil-now)/300f; c.translate((rnd.nextFloat()-.5f)*s,(rnd.nextFloat()-.5f)*s); }
        for(Bug b:bugs) drawBug(c,b,now);
        for(Power q:powers) drawPower(c,q,now);
        drawParticles(c,now); drawTexts(c,now);
        c.restore();
        if(now<flashUntil){p.setStyle(Paint.Style.FILL);p.setColor(flashColor);c.drawRect(0,0,getWidth(),getHeight(),p);}
        drawHud(c,now);
        postInvalidateOnAnimation();
    }

    private void update(float dt,long now){
        boolean frozen=now<freezeUntil; float top=dp(108), bottom=getHeight()-dp(70);
        for(Bug b:bugs){
            b.phase+=dt*(b.type==GOLD?8:5.2f);
            if(!frozen){
                b.x+=(b.vx+(float)Math.sin(b.phase*1.7f)*dp(b.type==GOLD?50:32))*dt;
                b.y+=(b.vy+(float)Math.cos(b.phase*1.3f)*dp(b.type==GOLD?40:26))*dt;
            }
            float r=b.size*.9f;
            if(b.x<r){b.x=r;b.vx=Math.abs(b.vx);} if(b.x>getWidth()-r){b.x=getWidth()-r;b.vx=-Math.abs(b.vx);}
            if(b.y<top+r){b.y=top+r;b.vy=Math.abs(b.vy);} if(b.y>bottom-r){b.y=bottom-r;b.vy=-Math.abs(b.vy);}
        }
        Iterator<Particle> pi=particles.iterator();
        while(pi.hasNext()){Particle x=pi.next();float age=(now-x.born)/(float)x.life;if(age>=1){pi.remove();continue;}x.x+=x.vx*dt;x.y+=x.vy*dt;x.vy+=dp(180)*dt;}
        Iterator<FloatText> ti=texts.iterator();
        while(ti.hasNext()){FloatText x=ti.next();if(now-x.born>=x.life)ti.remove();else x.y-=dp(26)*dt;}
        Iterator<Power> qi=powers.iterator(); while(qi.hasNext()) if(now-qi.next().born>7000)qi.remove();
        fillBugs();
    }

    private void fillBugs(){
        int n=0; for(Bug b:bugs)if(b.type!=BOSS)n++;
        int wanted=Math.min(7,3+(level-1)/2);
        while(n<wanted){bugs.add(makeBug(NORMAL));n++;}
    }

    private void spawnPower(long now){
        if(!powers.isEmpty() || now-lastPower<Math.max(7000,11000-level*300L))return;
        Power q=new Power();q.type=rnd.nextBoolean()?BOMB:FREEZE;q.x=dp(65)+rnd.nextFloat()*Math.max(1,getWidth()-dp(130));q.y=dp(170)+rnd.nextFloat()*Math.max(1,getHeight()-dp(350));q.born=now;q.phase=rnd.nextFloat()*7;powers.add(q);lastPower=now;
        text(q.x,q.y-dp(38),q.type==BOMB?"بمب!":"فریز!",0xFFFFFFFF,1200,dp(22));
        beep(ToneGenerator.TONE_PROP_BEEP2,80);
    }

    private void drawHud(Canvas c,long now){
        p.setStyle(Paint.Style.FILL);p.setColor(0xAA000000);c.drawRoundRect(new RectF(dp(14),dp(14),getWidth()-dp(14),dp(92)),dp(22),dp(22),p);
        tp.setTextSize(dp(25));tp.setColor(0xFFFFE66D);tp.setTextAlign(Paint.Align.LEFT);c.drawText("★ "+score,dp(28),dp(58),tp);
        tp.setTextSize(dp(17));tp.setColor(0xFFFFFFFF);tp.setTextAlign(Paint.Align.CENTER);c.drawText("مرحله "+level,getWidth()/2f,dp(55),tp);
        tp.setTextAlign(Paint.Align.RIGHT);tp.setTextSize(dp(17));tp.setColor(combo>=3?0xFFFF9F43:0xFFFFFFFF);c.drawText(combo>=3&&now-lastHit<1500?"کمبو ×"+mult():"پشه‌ها رو بزن!",getWidth()-dp(28),dp(56),tp);
        if(now<freezeUntil){float rem=(freezeUntil-now)/4200f;p.setColor(0xAA7FDBFF);c.drawRoundRect(new RectF(dp(40),dp(97),getWidth()-dp(40),dp(105)),dp(4),dp(4),p);p.setColor(0xFFFFFFFF);c.drawRoundRect(new RectF(dp(40),dp(97),dp(40)+(getWidth()-dp(80))*rem,dp(105)),dp(4),dp(4),p);}
        drawSound(c,dp(42),getHeight()-dp(44));
        if(score==0){p.setColor(0x99000000);float y=getHeight()*.74f;c.drawRoundRect(new RectF(dp(34),y-dp(38),getWidth()-dp(34),y+dp(28)),dp(20),dp(20),p);tp.setTextAlign(Paint.Align.CENTER);tp.setTextSize(dp(20));tp.setColor(0xFFFFFFFF);c.drawText("روی پشه‌ها ضربه بزن!",getWidth()/2f,y+dp(5),tp);}
        if(now<bannerUntil)banner(c,"آفرین!  مرحله "+level,0xEE27AE60);
        if(now<bossBannerUntil)banner(c,"غول پشه اومد!",0xEE8E44AD);
    }

    private void banner(Canvas c,String s,int color){float y=getHeight()*.23f;p.setColor(color);c.drawRoundRect(new RectF(dp(34),y-dp(38),getWidth()-dp(34),y+dp(34)),dp(28),dp(28),p);tp.setTextAlign(Paint.Align.CENTER);tp.setTextSize(dp(23));tp.setColor(0xFFFFFFFF);c.drawText(s,getWidth()/2f,y+dp(8),tp);}

    private void drawSound(Canvas c,float x,float y){
        p.setStyle(Paint.Style.FILL);p.setColor(0xAA000000);c.drawCircle(x,y,dp(27),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2.5f));p.setColor(0xFFFFFFFF);
        Path z=new Path();z.moveTo(x-dp(12),y-dp(5));z.lineTo(x-dp(5),y-dp(5));z.lineTo(x+dp(2),y-dp(12));z.lineTo(x+dp(2),y+dp(12));z.lineTo(x-dp(5),y+dp(5));z.lineTo(x-dp(12),y+dp(5));z.close();c.drawPath(z,p);
        if(sound)c.drawArc(new RectF(x-dp(2),y-dp(11),x+dp(15),y+dp(11)),-55,110,false,p);else{c.drawLine(x+dp(5),y-dp(8),x+dp(14),y+dp(8),p);c.drawLine(x+dp(14),y-dp(8),x+dp(5),y+dp(8),p);}
    }

    private void drawBug(Canvas c,Bug b,long now){
        c.save();c.translate(b.x,b.y);c.rotate((float)Math.toDegrees(Math.atan2(b.vy,b.vx))+90);float s=b.size,w=.76f+.24f*(float)Math.sin(now/(b.type==GOLD?34.0:50.0)+b.phase);
        if(b.type==GOLD){p.setStyle(Paint.Style.FILL);p.setColor(0x55FFD700);c.drawCircle(0,0,s*1.35f+(float)Math.sin(now/90.0)*dp(4),p);} else if(b.type==BOSS){p.setStyle(Paint.Style.FILL);p.setColor(0x558E44AD);c.drawCircle(0,0,s*1.25f+(float)Math.sin(now/100.0)*dp(5),p);}
        if(now<freezeUntil){p.setColor(0x557FDBFF);c.drawCircle(0,0,s*1.18f,p);}
        p.setColor(b.type==GOLD?0xCCFFF0A6:0xBBDDF4FF);c.save();c.rotate(-30*w);c.drawOval(new RectF(-s*.95f,-s*.58f,-s*.08f,s*.08f),p);c.restore();c.save();c.rotate(30*w);c.drawOval(new RectF(s*.08f,-s*.58f,s*.95f,s*.08f),p);c.restore();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(dp(1.5f),s*.055f));p.setColor(b.type==GOLD?0xFF9B6B00:0xFF202020);
        for(int side=-1;side<=1;side+=2)for(int i=0;i<3;i++){Path l=new Path();l.moveTo(side*s*.16f,-s*.02f+i*s*.24f);l.lineTo(side*s*(.55f+i*.06f),s*(.12f+i*.17f));l.lineTo(side*s*(.83f+i*.04f),s*(.36f+i*.15f));c.drawPath(l,p);}
        p.setStyle(Paint.Style.FILL);p.setColor(b.type==GOLD?0xFFFFC400:(b.type==BOSS?0xFF7D3C98:0xFF30343A));c.drawOval(new RectF(-s*.23f,-s*.45f,s*.23f,s*.48f),p);p.setColor(b.type==BOSS?0xFF4A235A:0xFF111111);c.drawCircle(0,-s*.54f,s*.24f,p);p.setColor(b.type==GOLD?0xFF1C7C54:0xFFFF3B30);c.drawCircle(-s*.1f,-s*.58f,s*.062f,p);c.drawCircle(s*.1f,-s*.58f,s*.062f,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(dp(1.5f),s*.05f));p.setColor(0xFF191919);c.drawLine(0,-s*.72f,0,-s*1.13f,p);c.restore();
        if(b.type==BOSS){float bw=dp(82),left=b.x-bw/2,y=b.y-b.size*1.32f;p.setStyle(Paint.Style.FILL);p.setColor(0x99000000);c.drawRoundRect(new RectF(left,y,left+bw,y+dp(9)),dp(5),dp(5),p);p.setColor(0xFFFF4D6D);c.drawRoundRect(new RectF(left,y,left+bw*b.hp/(float)b.maxHp,y+dp(9)),dp(5),dp(5),p);}
    }

    private void drawPower(Canvas c,Power q,long now){
        float y=q.y+(float)Math.sin((now-q.born)/250.0+q.phase)*dp(7),r=dp(29)*(1+.08f*(float)Math.sin((now-q.born)/125.0));p.setStyle(Paint.Style.FILL);p.setColor(q.type==BOMB?0xDDFF7A00:0xDD3DA5FF);c.drawCircle(q.x,y,r,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(4));p.setColor(0xFFFFFFFF);c.drawCircle(q.x,y,r+dp(4),p);
        if(q.type==BOMB){p.setStyle(Paint.Style.FILL);p.setColor(0xFF222222);c.drawCircle(q.x,y+dp(4),dp(12),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));Path f=new Path();f.moveTo(q.x+dp(7),y-dp(7));f.quadTo(q.x+dp(12),y-dp(17),q.x+dp(19),y-dp(12));c.drawPath(f,p);p.setStyle(Paint.Style.FILL);p.setColor(0xFFFFE66D);c.drawCircle(q.x+dp(20),y-dp(12),dp(4),p);}else{p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));for(int i=0;i<3;i++){double a=Math.PI*i/3;float dx=(float)Math.cos(a)*dp(14),dy=(float)Math.sin(a)*dp(14);c.drawLine(q.x-dx,y-dy,q.x+dx,y+dy,p);}}
    }

    private void drawParticles(Canvas c,long now){for(Particle x:particles){float age=(now-x.born)/(float)x.life;int a=(int)(255*(1-age));p.setStyle(Paint.Style.FILL);p.setColor((a<<24)|(x.color&0xFFFFFF));c.drawCircle(x.x,x.y,x.r*(1-.35f*age),p);}}
    private void drawTexts(Canvas c,long now){for(FloatText x:texts){float age=(now-x.born)/(float)x.life;int a=(int)(255*(1-age));tp.setTextAlign(Paint.Align.CENTER);tp.setTextSize(x.size*(1+.15f*age));tp.setColor((a<<24)|(x.color&0xFFFFFF));c.drawText(x.s,x.x,x.y,tp);}}

    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getActionMasked()!=MotionEvent.ACTION_DOWN)return true;float x=e.getX(),y=e.getY();long now=SystemClock.uptimeMillis();
        if(dist(x,y,dp(42),getHeight()-dp(44))<dp(34)){sound=!sound;performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);return true;}
        Iterator<Power> qi=powers.iterator();while(qi.hasNext()){Power q=qi.next();float py=q.y+(float)Math.sin((now-q.born)/250.0+q.phase)*dp(7);if(dist(x,y,q.x,py)<dp(44)){activate(q,now);qi.remove();return true;}}
        Bug hit=null;float best=Float.MAX_VALUE;for(Bug b:bugs){float d=dist(x,y,b.x,b.y);if(d<b.size*(b.type==BOSS?1.45f:1.32f)&&d<best){best=d;hit=b;}}
        if(hit!=null)hit(hit,now);else{combo=0;burst(x,y,0xFFFFFFFF,5,now,.35f);}
        return true;
    }

    private void hit(Bug b,long now){
        combo=now-lastHit<1400?combo+1:1;lastHit=now;int m=mult();performHapticFeedback(b.type==BOSS?HapticFeedbackConstants.LONG_PRESS:HapticFeedbackConstants.CLOCK_TICK);
        if(b.type==GOLD)beep(ToneGenerator.TONE_PROP_ACK,120);else if(b.type==BOSS)beep(ToneGenerator.TONE_PROP_NACK,90);else beep(ToneGenerator.TONE_PROP_BEEP,65);
        b.hp--;int col=b.type==GOLD?0xFFFFD700:(b.type==BOSS?0xFFB66DFF:0xFFFF5C5C);burst(b.x,b.y,col,b.type==BOSS?24:16,now,1);shakeUntil=now+(b.type==BOSS?240:120);flashUntil=now+90;flashColor=b.type==GOLD?0x44FFD700:0x33FFFFFF;
        if(b.hp>0){score+=m;text(b.x,b.y-dp(26),"+"+m,0xFFFFFFFF,650,dp(24));b.vx*=-1.1f;b.vy*=-1.1f;progress(now);return;}
        int gain=b.value*m;score+=gain;text(b.x,b.y-dp(24),b.type==GOLD?"+"+gain+" طلایی!":b.type==BOSS?"+"+gain+" غول!":"+"+gain,b.type==GOLD?0xFFFFE45C:0xFFFFFFFF,900,dp(b.type==BOSS?28:24));if(combo>=3)text(b.x,b.y+dp(28),"کمبو!",0xFFFF9F43,750,dp(19));bugs.remove(b);progress(now);fillBugs();
    }

    private int mult(){return Math.min(5,1+Math.max(0,combo-1)/3);}

    private void progress(long now){
        int nl=1+score/25;if(nl>level){level=nl;bannerUntil=now+1800;beep(ToneGenerator.TONE_PROP_ACK,180);confetti(now);}
        if(score>=nextBoss&&!hasBoss()){bugs.add(makeBug(BOSS));bossBannerUntil=now+1800;beep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,160);nextBoss+=30;}
    }
    private boolean hasBoss(){for(Bug b:bugs)if(b.type==BOSS)return true;return false;}

    private void activate(Power q,long now){
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);beep(ToneGenerator.TONE_PROP_ACK,180);flashUntil=now+180;
        if(q.type==FREEZE){freezeUntil=now+4200;flashColor=0x557FDBFF;text(q.x,q.y,"همه فریز شدن!",0xFFBDEBFF,1200,dp(24));burst(q.x,q.y,0xFF7FDBFF,30,now,1.2f);return;}
        flashColor=0x66FFB000;shakeUntil=now+320;int killed=0;Iterator<Bug> it=bugs.iterator();while(it.hasNext()){Bug b=it.next();burst(b.x,b.y,b.type==GOLD?0xFFFFD700:0xFFFF6B4A,10,now,1);if(b.type==BOSS){b.hp-=2;if(b.hp<=0){score+=10;it.remove();killed++;}}else{score+=Math.min(3,b.value);it.remove();killed++;}}
        text(q.x,q.y,"بــــوم!  +"+killed,0xFFFFE66D,1200,dp(30));progress(now);fillBugs();
    }

    private void burst(float x,float y,int color,int n,long now,float power){for(int i=0;i<n;i++){Particle z=new Particle();z.x=x;z.y=y;double a=rnd.nextDouble()*Math.PI*2;float sp=dp((70+rnd.nextInt(220))*power);z.vx=(float)Math.cos(a)*sp;z.vy=(float)Math.sin(a)*sp-dp(50);z.r=dp(3+rnd.nextInt(5));z.color=i%4==0?0xFFFFFFFF:color;z.born=now;z.life=450+rnd.nextInt(450);particles.add(z);}}
    private void confetti(long now){int[] cs={0xFFFF5C5C,0xFFFFD93D,0xFF6BCB77,0xFF4D96FF,0xFFB983FF,0xFFFFFFFF};for(int i=0;i<70;i++){Particle z=new Particle();z.x=rnd.nextFloat()*getWidth();z.y=dp(80+rnd.nextInt(100));z.vx=dp(-100+rnd.nextInt(200));z.vy=dp(-40+rnd.nextInt(170));z.r=dp(3+rnd.nextInt(5));z.color=cs[rnd.nextInt(cs.length)];z.born=now;z.life=1200+rnd.nextInt(600);particles.add(z);}}
    private void text(float x,float y,String s,int color,long life,float size){FloatText z=new FloatText();z.x=x;z.y=y;z.s=s;z.color=color;z.life=life;z.size=size;z.born=SystemClock.uptimeMillis();texts.add(z);}
    private void beep(int kind,int ms){if(sound)tone.startTone(kind,ms);}
    private float dist(float x1,float y1,float x2,float y2){float dx=x1-x2,dy=y1-y2;return(float)Math.sqrt(dx*dx+dy*dy);}
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    @Override protected void onDetachedFromWindow(){tone.release();super.onDetachedFromWindow();}

    private static class Bug{int type,hp,maxHp,value;float x,y,vx,vy,size,phase;}
    private static class Particle{float x,y,vx,vy,r;int color;long born,life;}
    private static class FloatText{float x,y,size;int color;String s;long born,life;}
    private static class Power{int type;float x,y,phase;long born;}
}
