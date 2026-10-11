"""Draws the Toma icon (512x512) and the Play feature graphic (1024x500) with Pillow.
Run: python3 publicar/make_assets.py  (writes into publicar/assets/)."""
import math, os
from PIL import Image, ImageDraw, ImageFont

GREEN=(31,94,85); CORAL=(227,106,74); CREAM=(255,253,248); PAPER=(246,241,231)
OUT=os.path.join(os.path.dirname(__file__),"assets"); os.makedirs(OUT,exist_ok=True)
S=4  # supersampling

def capsule(size, angle=-40, scale=1.0, check=True):
    big=size*S
    img=Image.new("RGBA",(big,big),(0,0,0,0))
    L=int(big*0.66*scale); H=int(big*0.28*scale)
    cap=Image.new("RGBA",(big,big),(0,0,0,0)); d=ImageDraw.Draw(cap)
    x0=(big-L)//2; y0=(big-H)//2
    d.rounded_rectangle([x0,y0,x0+L,y0+H],radius=H//2,fill=CREAM)
    d.rounded_rectangle([x0+L//2-H//2,y0,x0+L,y0+H],radius=H//2,fill=CORAL)
    d.rectangle([x0+L//2-H//2,y0,x0+L//2+1,y0+H],fill=CREAM)
    d.rectangle([x0+L//2,y0,x0+L//2+H//2,y0+H],fill=CORAL)
    cap=cap.rotate(-angle,resample=Image.BICUBIC,center=(big/2,big/2))
    img.alpha_composite(cap)
    if check:
        d=ImageDraw.Draw(img); w=int(big*0.05)
        pts=[(big*0.43,big*0.52),(big*0.49,big*0.58),(big*0.60,big*0.45)]
        d.line(pts,fill=GREEN,width=w,joint="curve")
        for p in (pts[0],pts[2]): d.ellipse([p[0]-w/2,p[1]-w/2,p[0]+w/2,p[1]+w/2],fill=GREEN)
    return img

icon=Image.new("RGBA",(512*S,512*S),GREEN+(255,))
icon.alpha_composite(capsule(512))
icon.resize((512,512),Image.LANCZOS).convert("RGB").save(os.path.join(OUT,"icono-512.png"))

W,H=1024,500
bg=Image.new("RGB",(W*S,H*S),GREEN)
d=ImageDraw.Draw(bg)
# blister-pack dots
for r in range(3):
    for c in range(8):
        cx=(560+c*55)*S; cy=(330+r*50)*S; taken=(r*8+c)%3!=2
        d.ellipse([cx-18*S,cy-18*S,cx+18*S,cy+18*S],outline=PAPER if not taken else CORAL,width=3*S,fill=CORAL if taken else None)
cap=capsule(420,scale=1.15).resize((420*S,420*S))
bg.paste(cap,(40*S,40*S),cap)
bg=bg.resize((W,H),Image.LANCZOS)
d=ImageDraw.Draw(bg)
def font(sz):
    for p in ("/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf","/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf"):
        if os.path.exists(p): return ImageFont.truetype(p,sz)
    return ImageFont.load_default()
d.text((470,110),"Toma",font=font(84),fill=CREAM)
d.text((472,215),"Una alarma que insiste",font=font(36),fill=PAPER)
d.text((472,262),"hasta que la tomas.",font=font(36),fill=PAPER)
bg.save(os.path.join(OUT,"grafico-funciones-1024x500.png"))
print("ok")
