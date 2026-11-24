package com.example.simulation

object SeaShaders {
    const val VERTEX_SHADER = """
attribute vec2 a;
void main() {
    gl_Position = vec4(a, 0.0, 1.0);
}
"""

    const val SCENE_FRAGMENT_SHADER = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform vec2 uRes,uTilt;
uniform float uTime,uCloud,uWind,uRain,uFog,uFlash,uMoonPh,uMoonL,uStar,uWDir,uStarRot,uBow,uBolt,uShip,uNight;
uniform vec3 uObj,uLook;uniform vec2 uLH;uniform sampler2D uOv;
uniform vec3 uSun,uSunL,uSunD,uMoon,uAmb,uPole;
uniform sampler2D uSky,uNrm;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float hash3(vec3 p){return fract(sin(dot(p,vec3(127.1,311.7,74.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);
 return mix(mix(hash(i),hash(i+vec2(1.,0.)),f.x),mix(hash(i+vec2(0.,1.)),hash(i+vec2(1.,1.)),f.x),f.y);}
float fbm(vec2 p){float a=.5,s=0.;for(int i=0;i<5;i++){s+=a*noise(p);p=mat2(1.6,1.2,-1.2,1.6)*p;a*=.5;}return s;}
vec3 lut(vec3 d){float az=clamp(atan(d.x,d.z),-1.6,1.6);float el=asin(clamp(d.y,0.,1.));
 vec2 sz=vec2(64.,32.),tc=vec2(.5+az/3.2,sqrt(el/1.5707963))*sz-.5,i=floor(tc),fr=tc-i;fr=fr*fr*fr*(fr*(fr*6.-15.)+10.);
 vec3 c=texture2D(uSky,(i+fr+.5)/sz).rgb+(hash(gl_FragCoord.xy*1.31)-.5)/170.;return c*c*4.;}
vec3 rotAxis(vec3 v,vec3 k,float a){float c=cos(a),s=sin(a);return v*c+cross(k,v)*s+k*dot(k,v)*(1.-c);}
float starField(vec3 rd){
 vec3 q=rotAxis(rd,uPole,uStarRot);vec3 g=q*72.;vec3 id=floor(g);vec3 f=fract(g)-.5;
 float h=hash3(id);vec3 o=vec3(hash3(id+1.7),hash3(id+3.1),hash3(id+5.3))-.5;
 float dd=length(f-o*.7);float px=72.*1.4/uRes.y;
 float b=smoothstep(max(.10,px),0.,dd)*step(.982,h)*(.25+.75*hash3(id+9.1));
 return b*(.75+.25*sin(uTime*3.+h*60.));}
float milky(vec3 rd){vec3 q=rotAxis(rd,uPole,uStarRot);float band=exp(-pow(dot(q,normalize(vec3(.46,.12,.88)))*5.,2.));
 return band*(.35+.65*fbm(q.xz*5.+q.y*3.))*.5;}
vec4 clouds(vec3 rd){
 float m=smoothstep(0.,.07,rd.y);
 vec2 p=rd.xz/(rd.y+.18)*.55+vec2(uTime*.010*(.4+uWind),uTime*.004);
 float f1=fbm(p*1.4);
 float d=f1*.7+fbm(p*3.5+3.1)*.3;
 float thr=.72-uCloud*.62;
 float a=smoothstep(thr,thr+.17,d)*m;
 float d2=fbm((p+uSun.xz*.07)*1.4);
 float lit=clamp(.62+(f1-d2)*5.,0.,1.);
 float thick=clamp((d-thr)*3.,0.,1.);
 vec3 col=uAmb*(.9-.4*thick-.25*uCloud*smoothstep(.2,.9,rd.y))+uSunL*lit*(1.-.65*thick)*.75;
 col*=1.-.55*uRain;
 return vec4(col,a);}
vec3 sky(vec3 rd,bool disk){
 vec3 c=lut(rd);
 float up=step(0.,rd.y);
 if(uStar>.01&&rd.y>0.){c+=vec3(.9,.95,1.)*starField(rd)*uStar*1.6+vec3(.5,.6,.9)*milky(rd)*uStar*.35;}
 if(uMoon.y>-.1){float R=.038;vec3 t1=normalize(cross(vec3(0.,1.,0.),uMoon));vec3 t2=cross(uMoon,t1);
  vec2 l=vec2(dot(rd,t1),dot(rd,t2))/R;float r2=dot(l,l);
  if(dot(rd,uMoon)>0.){
   float a=6.2831853*uMoonPh;
   if(r2<1.){vec3 n=vec3(l.x,l.y,sqrt(1.-r2));float lam=dot(n,vec3(sin(a),0.,-cos(a)));
    float alb=.55+.45*fbm(l*3.+vec2(2.,5.));alb*=.8+.2*fbm(l*11.);
    c+=vec3(1.,.97,.9)*(clamp(lam*3.,0.,1.)*alb+.03)*uMoonL;}
   c+=vec3(.55,.65,.9)*uMoonL*.12*exp(-r2*.18);}}
 vec4 cl=clouds(rd);
 c=mix(c,cl.rgb,cl.a);
 float ci=fbm(vec2(rd.x*2.,rd.z*9.)/(rd.y+.35)+uTime*.01)*smoothstep(.05,.45,rd.y)*(1.-uCloud*.7)*.5;
 c=mix(c,uAmb*1.3+uSunL*.25,clamp((ci-.18)*1.3,0.,.4));
 if(disk){float s=smoothstep(.99989,.99996,dot(rd,uSun));c+=uSunD*s*(1.-cl.a)*(1.-uCloud*.75)*up;}
 {float sa=dot(rd,uSun);if(sa>.35&&uSun.y>-.04){vec3 t1=normalize(cross(vec3(0.,1.,0.),uSun));vec3 t2=cross(uSun,t1);
   float an=atan(dot(rd,t2),dot(rd,t1));float ray=fbm(vec2(an*6.,uTime*.02))*fbm(vec2(an*13.+4.,uTime*.03));
   float k=pow(max(sa,0.),7.)*smoothstep(.2,.75,uCloud)*(1.-.35*uCloud)*smoothstep(-.04,.2,uSun.y)*(1.-smoothstep(.55,.9,uSun.y));
   c+=uSunL*ray*k*.55*(.4+.6*(1.-cl.a));}}
 if(uBow>.01){float ang=acos(clamp(dot(rd,-uSun),-1.,1.))*57.2958;
  for(int i=0;i<2;i++){float c0=i==0?41.:51.5;float x=(ang-(c0-1.6))/3.2;
   if(x>0.&&x<1.){float hue=(i==0?.76*(1.-x):.76*x);vec3 rb=clamp(abs(mod(hue*6.+vec3(0.,4.,2.),6.)-3.)-1.,0.,1.);
    float w=smoothstep(0.,.2,x)*smoothstep(1.,.8,x);c+=rb*w*uBow*(i==0?.9:.35)*(uAmb.g*3.+.05)*(1.-cl.a)*up;}}}
 c+=vec3(.55,.6,.8)*uFlash*(.35+.65*smoothstep(0.,.6,rd.y))*1.2*up;
 if(uFlash>.3){float x0=(uBolt-.5)*.9;float yy=rd.y/max(rd.z,.2);float ax=rd.x/max(rd.z,.2);
  float path=x0+(fbm(vec2(yy*22.,uBolt*9.))-.5)*.16+(fbm(vec2(yy*70.,uBolt*5.))-.5)*.04;
  c+=vec3(.7,.75,1.)*smoothstep(.012,0.,abs(ax-path))*step(0.,yy)*(1.-smoothstep(.2,.5,yy))*uFlash*1.8;}
 return c;}
vec4 objects(vec3 rd,out vec3 glow){
 float ax=atan(rd.x,rd.z),ay=asin(clamp(rd.y,-1.,1.));float pa=1.3/(uRes.y*1.15);
 vec3 col=vec3(0.);float al=0.;glow=vec3(0.);
 float la=atan(uSun.x,uSun.z);
 vec3 hzc=lut(normalize(vec3(rd.x,.012,max(abs(rd.z),.1))));
 if(uObj.x>.5){float u=ax-uLH.x;
  if(abs(u)<.22&&ay<.16&&ay>-.03){
   float by=-.0025,h=.07,w0=.0072,w1=.0048;
   float ux=abs(u)/.05;float isl=by+.0085*pow(max(1.-ux*ux,0.),1.6)*(.85+.3*noise(vec2(u*160.,2.)));
   float ti=(ay-(by+.007))/h;float tw=mix(w0,w1,clamp(ti,0.,1.));
   float nx=clamp(u/max(tw,1e-4),-1.,1.);float lam=clamp(nx*sin(la-uLH.x)+sqrt(1.-nx*nx)*cos(la-uLH.x)*.7+.15,0.,1.);
   vec3 lit=uAmb*2.2+uSunL*lam*.55;
   float aI=smoothstep(-pa,pa,isl-ay)*smoothstep(-pa,pa,ay+.0018)*step(abs(u),.05);
   col=vec3(.11,.1,.085)*(uAmb*2.4+uSunL*.45*(.3+.7*lam));al=aI;
   float inT=smoothstep(-pa,pa,tw-abs(u))*step(-.02,ti)*step(ti,1.);
   float band=step(.42,ti)*step(ti,.58);
   vec3 tc=mix(vec3(.9,.88,.84),vec3(.75,.12,.1),band)*lit*(.6+.4*(1.-abs(nx)*.5));
   col=mix(col,tc,inT);al=max(al,inT);
   float inG=smoothstep(-pa,pa,.0075-abs(u))*step(.97,ti)*step(ti,1.02);col=mix(col,vec3(.05)*lit,inG);al=max(al,inG);
   float inL=smoothstep(-pa,pa,.0036-abs(u))*step(1.02,ti)*step(ti,1.12);
   col=mix(col,mix(vec3(.04,.05,.07)*lit,vec3(1.,.82,.45)*(2.+uNight*30.),uNight),inL);al=max(al,inL);
   float ry=ti-1.12;float inR=smoothstep(-pa,pa,.0058*(1.-ry/.09)-abs(u))*step(0.,ry)*step(ry,.09);
   col=mix(col,vec3(.55,.08,.07)*lit,inR);al=max(al,inR);
   col=mix(col,hzc*(1.+.3*uFog),.42);}
  if(uNight>.01){float u2=ax-uLH.x,ayl=-.0025+.007+.07*1.07,dy=ay-ayl;
   glow+=vec3(1.,.8,.5)*exp(-(u2*u2+dy*dy)/(.0085*.0085))*uNight*5.;
   for(int i=0;i<2;i++){float b=uTime*.7+float(i)*3.14159;float d=cos(b),sn=sin(b);
    float along=u2/(d*.7+(d<0.?-.03:.03));float vis=smoothstep(-.4,.2,sn)*step(0.,along)*step(along,1.2);
    float wd=.004+.012*along*along;glow+=vec3(1.,.9,.65)*vis*exp(-pow(dy/wd,2.))*(1.-along*.8)*uNight*1.2;}
   float fl=pow(max(sin(uTime*.7),0.),24.)+pow(max(-sin(uTime*.7),0.),24.);
   glow+=vec3(1.,.85,.6)*exp(-(u2*u2+dy*dy)/(.03*.03))*fl*uNight*4.;}}
 return vec4(col,al);}
float swell(vec2 p,out vec2 gr){float h=0.;gr=vec2(0.);float S=.04+.24*uWind;
 for(int i=0;i<6;i++){float fi=float(i);float k=.2*pow(1.5,fi);float ang=uWDir+sin(fi*5.31)*.85;vec2 d=vec2(cos(ang),sin(ang));
  float a=S*pow(.88,fi)/k*1.1;float th=k*dot(d,p)+sqrt(9.8*k)*uTime*(.45+.55*uWind)+fi*1.7;
  float e=exp(sin(th)-1.);h+=a*(e-.4658);gr+=d*a*e*cos(th)*k;}
 return h;}
vec3 sea(vec3 ro,vec3 rd){
 float t=ro.y/(-rd.y);vec2 gr;
 if(t<500.){for(int i=0;i<3;i++){vec3 p=ro+rd*t;float h=swell(p.xz,gr);t+=(p.y-h)/(-rd.y)*.9;}}
 t=clamp(t,0.,6000.);vec3 p=ro+rd*t;float h0=swell(p.xz,gr);
 float fade=exp(-t*.012);float mv=.5+uWind;
 vec4 n1=texture2D(uNrm,p.xz*.11+vec2(uTime*.011,uTime*.007)*mv),n2=texture2D(uNrm,p.xz*.33+vec2(-uTime*.02,uTime*.014)*mv);
 float A=uWind*.8+.06;
 vec2 dn=((n1.rg*2.-1.)*.7+(n2.rg*2.-1.)*.5)*A*.35*(.3+.7*fade);
 vec3 n=normalize(vec3(-gr.x+dn.x,1.,-gr.y+dn.y));
 n=normalize(mix(n,vec3(0.,1.,0.),clamp(t/400.,0.,.85)));
 vec3 r=reflect(rd,n);r.y=abs(r.y);
 float F=.02+.98*pow(clamp(1.-dot(n,-rd),0.,1.),5.);
 vec3 refl=sky(r,false);
 vec3 gl2;vec4 ob2=objects(r,gl2);refl=mix(refl,ob2.rgb,ob2.a)+gl2;
 float cloudOcc=1.-uCloud*.8;
 refl+=uSunD*.012*pow(max(dot(r,uSun),0.),900.)*cloudOcc+uSunL*.15*pow(max(dot(r,uSun),0.),40.)*cloudOcc;
 float sss=clamp(h0*.7+.45,0.,1.);
 vec3 body=(vec3(.01,.05,.075)+vec3(.04,.16,.13)*sss)*(uAmb*2.4*(1.-.4*uCloud-.35*uRain)+uSunL*max(dot(n,uSun),0.)*.35);
 vec3 col=mix(body,refl,F);
 vec2 wd=vec2(cos(uWDir),sin(uWDir));
 float fs=texture2D(uNrm,vec2(dot(p.xz,wd)*.09-uTime*.07,dot(p.xz,vec2(-wd.y,wd.x))*.36)).b;
 float crest=smoothstep(.50,1.0,h0*1.25+fs*.8-.25+uWind*.2);
 float ftex=smoothstep(.45,.75,n1.b)*smoothstep(.35,.7,n2.a);
 float foam=clamp(crest*smoothstep(.45,.9,uWind)*(.25+1.1*ftex)*(1.-clamp(t/600.,0.,1.)),0.,1.);
 vec3 foamC=(uAmb*1.8+uSunL*(.25+.5*max(dot(n,uSun),0.)))*.8;
 col=mix(col,foamC,foam*.8);
 float spray=foam*smoothstep(.5,1.,uWind);
 col+=foamC*step(1.-spray*.07,hash(floor(gl_FragCoord.xy*.6)+floor(uTime*28.)))*.9;
 col=mix(col,foamC*.9,smoothstep(.6,1.,uWind)*.18*exp(-t*.004));
 float fg=1.-exp(-t*(.0007+uFog*.014+uRain*.003));
 col=mix(col,lut(normalize(vec3(rd.x,.012,rd.z)))*(1.+uFog*.6),fg);
 return col;}
float rainL(vec2 uv){float s=0.;
 for(int i=0;i<3;i++){float fi=float(i);float N=42.+fi*38.;vec2 q=vec2(uv.x+uv.y*.28*(.3+uWind),uv.y);
  float col=floor(q.x*N);float rnd=hash(vec2(col,fi*7.3+1.));float lx=abs(fract(q.x*N)-.5);
  float y=fract(q.y*(1.1+fi*.45)+uTime*(1.3+fi*.55)*(.7+rnd)+rnd*9.);float len=.10+.12*rnd;
  float st=smoothstep(1.-len,1.,y)*(1.-smoothstep(.985,1.,y));
  s+=smoothstep(.13,.05,lx)*st*step(.42,rnd)*(.55-.16*fi);}
 return s;}
vec3 aces(vec3 x){return clamp((x*(2.51*x+.03))/(x*(2.43*x+.59)+.14),0.,1.);}
void main(){
 vec2 uv=(gl_FragCoord.xy-.5*uRes)/uRes.y;
 float sw=uWind*uWind*uWind;
 vec3 ro=vec3(0.,4.5+sin(uTime*.6)*.7*sw,0.);
 float rl=sin(uTime*.45)*.04*sw+uLook.y;vec2 uv2=vec2(uv.x*cos(rl)-uv.y*sin(rl),uv.x*sin(rl)+uv.y*cos(rl));
 vec3 rd=normalize(vec3(uv2.x+uTilt.x*.12,uv2.y+uTilt.y*.12-.10+tan(uLook.x)*1.15,1.15));
 vec3 c=rd.y>=0.?sky(rd,true):sea(ro,rd);
 {vec3 g1;vec4 ob=objects(rd,g1);c=mix(c,ob.rgb,ob.a)+g1;}
 c*=1.-.4*uRain*smoothstep(.45,1.,uWind);
 c=aces(c);c=pow(c,vec3(1./2.2));
 {vec4 ov=texture2D(uOv,vec2(gl_FragCoord.x/uRes.x,1.-gl_FragCoord.y/uRes.y));c=c*(1.-ov.a)+ov.rgb;}
 c+=vec3(.8,.85,.95)*rainL(uv)*uRain*.55;
 c=mix(c,c*c*(3.-2.*c),.25);
 c+=(hash(gl_FragCoord.xy+fract(uTime)*91.)-.5)*.02;
 c*=1.-.28*smoothstep(.5,1.15,length(uv*vec2(.9,1.)));
 gl_FragColor=vec4(clamp(c,0.,1.),1.);
}
"""

    const val GLASS_FRAGMENT_SHADER = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D uScene;uniform vec2 uRes,uSceneRes;uniform float uTime,uRain,uFog,uDrops;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
vec3 bicubic(vec2 uv){
 vec2 tc=uv*uSceneRes,tc1=floor(tc-.5)+.5,f=tc-tc1;
 vec2 w0=f*(-.5+f*(1.-.5*f)),w1=1.+f*f*(-2.5+1.5*f),w2=f*(.5+f*(2.-1.5*f)),w3=f*f*(-.5+.5*f);
 vec2 w12=w1+w2,o12=w2/w12;
 vec2 t0=(tc1-1.)/uSceneRes,t3=(tc1+2.)/uSceneRes,t12=(tc1+o12)/uSceneRes;
 vec3 c=texture2D(uScene,vec2(t12.x,t0.y)).rgb*(w12.x*w0.y)+texture2D(uScene,vec2(t0.x,t12.y)).rgb*(w0.x*w12.y)+texture2D(uScene,t12).rgb*(w12.x*w12.y)
  +texture2D(uScene,vec2(t3.x,t12.y)).rgb*(w3.x*w12.y)+texture2D(uScene,vec2(t12.x,t3.y)).rgb*(w12.x*w3.y);
 return max(c/(w12.x*w0.y+w0.x*w12.y+w12.x*w12.y+w3.x*w12.y+w12.x*w3.y),0.);}
vec4 dropShape(vec2 p,vec2 c,vec2 rad,float seed,out float caus){
 vec2 q=(p-c)/rad;float an=atan(q.y,q.x);float wob=1.+.1*sin(3.*an+seed*6.)+.06*sin(5.*an+seed*11.);
 float r=length(q)/wob;float inside=smoothstep(1.,.88,r);
 vec2 off=-q*rad*(1.5+.9*r*r)*inside;
 float hl=smoothstep(.3,0.,length(q-vec2(-.38,.42)))*inside;
 float edge=smoothstep(.5,1.,r)*inside;
 caus=smoothstep(.5,.95,r)*smoothstep(-.2,.9,-q.y/(length(q)+.001))*inside;
 return vec4(off,hl,edge);}
void layerStatic(vec2 uv,float sc,float seed,float prob,out vec2 off,out float hl,out float edge,out float caus){
 float asp=uRes.x/uRes.y;vec2 st=vec2(uv.x*asp,uv.y)*sc;vec2 id=floor(st),f=fract(st);
 float h=hash(id+seed),h2=hash(id+seed+11.3),h3=hash(id+seed+23.7);
 float life=fract(uTime*(.008+.012*h2)+h3);float vis=smoothstep(0.,.1,life)*smoothstep(1.,.8,life)*step(h,prob);
 vec2 c=vec2(.25+.5*h2,.25+.5*h3);float rr=.08+.22*h*h/(prob+.01);rr=min(rr,.3);
 vec2 rad=vec2(rr,rr*(1.1+.25*h2));
 vec4 d=dropShape(f,c,rad,h+seed,caus);caus*=vis;
 off=d.xy*vis/vec2(sc*asp,sc);hl=d.z*vis;edge=d.w*vis;}
void layerSlide(vec2 uv,float sc,float seed,float prob,out vec2 off,out float hl,out float edge,out float caus,out float trail){
 float asp=uRes.x/uRes.y;vec2 st=vec2(uv.x*asp,uv.y*.55)*sc;vec2 id=floor(st),f=fract(st);
 float h=hash(id+seed),h2=hash(id+seed+11.3),h3=hash(id+seed+23.7);off=vec2(0.);hl=edge=caus=trail=0.;
 float on=step(h,prob);if(on<.5)return;
 float ph=uTime*(.045+.05*h2)+h3;float y=fract(ph+.05*sin(ph*21.+h*9.));float head=1.-y;
 float xh=.5+(h2-.5)*.4+.03*sin(f.y*8.+h3*6.);
 float L=1.1;float above=f.y-head;
 float tw=.02*(1.-.5*clamp(above/L,0.,1.));
 float tl=smoothstep(tw,tw*.3,abs(f.x-xh))*step(0.,above)*step(above,L)*(1.-above/L);
 trail=tl;off+=vec2((f.x-xh)/tw*.0016,0.)*tl;
 for(int i=0;i<4;i++){float fi=float(i);float by=head+.16+fi*.17;float bh=hash(id+seed+fi*7.7);
  if(bh>.35&&by<head+L){float rr=.03+.015*(1.-fi*.2);float cc;vec4 d=dropShape(f,vec2(xh+(bh-.5)*.03,by),vec2(rr,rr*1.15),bh,cc);
   off+=d.xy/vec2(sc*asp,sc*.55);hl+=d.z;edge=max(edge,d.w);caus+=cc;}}
 float rr=.06+.05*h2;float cc;vec4 d=dropShape(f,vec2(xh,head),vec2(rr,rr*1.55),h,cc);
 off+=d.xy/vec2(sc*asp,sc*.55);hl+=d.z;edge=max(edge,d.w);caus+=cc;}
void main(){
 vec2 uv=gl_FragCoord.xy/uRes;vec2 off=vec2(0.);float hl=0.,edge=0.,caus=0.,tr=0.;
 if(uDrops>.5&&uRain>.03){
  vec2 o;float a,b,c,t;
  layerSlide(uv,8.,3.1,uRain*.8+.06,o,a,b,c,t);off+=o;hl+=a;edge=max(edge,b);caus+=c;tr=max(tr,t);
  layerStatic(uv,9.,17.,uRain*.8+.04,o,a,b,c);off+=o;hl+=a;edge=max(edge,b);caus+=c;
  layerStatic(uv,17.,41.,uRain*.85+.05,o,a,b,c);off+=o*.9;hl+=a;edge=max(edge,b);caus+=c;
  layerStatic(uv,52.,71.,uRain*.7+uFog*.3,o,a,b,c);off+=o*.5;hl+=a*.5;edge=max(edge,b*.6);}
 vec2 suv=clamp(uv+off,.001,.999);
 vec3 c=bicubic(suv);
 c+=(c-texture2D(uScene,suv).rgb)*.55;
 float lum=dot(c,vec3(.33));
 c*=1.-edge*.42;c+=vec3(.9,.95,1.)*caus*.22*(.35+lum);c+=vec3(1.)*hl*.55;c=mix(c,c*1.2+.03,tr*.8);
 c=mix(c,vec3(dot(c,vec3(.33)))*1.04+.01,uFog*.22);
 c+=(hash(gl_FragCoord.xy)+hash(gl_FragCoord.xy+17.3)-1.)/255.;
 gl_FragColor=vec4(clamp(c,0.,1.),1.);}
"""
}
