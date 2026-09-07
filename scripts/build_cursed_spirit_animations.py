#!/usr/bin/env python3
"""Export authored pixel cels, runtime bindings/choreography, and review boards.

Requires Pillow. Content lives in animation_art/cursed_spirits.json; this renderer
only knows reusable drawing primitives, never move IDs or combat rules. Existing
packs and choreography entries outside the cs- namespace are preserved.
"""

import argparse
import hashlib
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ART = Path(__file__).with_name("animation_art") / "cursed_spirits.json"
COLORS = {
    "curse": ["#14243a", "#264f62", "#327d87", "#57c7b1", "#b6ffe0", "#f2ffec"],
    "venom": ["#242039", "#5c326d", "#a54e9c", "#d286cc", "#eac2ee", "#fff0fd"],
    "bark": ["#292337", "#55434a", "#8a6251", "#b48d65", "#d8c48b", "#f2ebbd"],
    "leaf": ["#192e36", "#345448", "#52865e", "#8abc72", "#d0e994", "#fffbd2"],
    "bloom": ["#402940", "#7b4064", "#bf6387", "#ed96af", "#ffd1c3", "#fff6dc"],
    "flesh": ["#28263e", "#53435c", "#86627b", "#c391a4", "#edc3c4", "#fff0db"],
    "bone": ["#25283c", "#484d69", "#7d8396", "#b7b7b5", "#e2ddc9", "#fffbe7"],
    "soul": ["#251d3c", "#4e396f", "#8155a3", "#bd87d4", "#e7c4ec", "#fff3ff"],
    "blood": ["#301e36", "#66304b", "#a83d60", "#e66b7a", "#ffb497", "#fff0cc"],
}


def point(x, y):
    return round(x), round(y)


def primitive(kind, palette, phase):
    """96px cels, hard edges and stepped highlights, with no blurred glow."""
    image = Image.new("RGBA", (96, 96))
    d = ImageDraw.Draw(image)
    p = COLORS[palette]

    def line(points, color, width=1):
        d.line([point(*v) for v in points], fill=color, width=width)

    def ellipse(x, y, rx, ry, color, width=0):
        box = (round(x-rx), round(y-ry), round(x+rx), round(y+ry))
        d.ellipse(box, fill=color if not width else None, outline=color, width=max(1, width))

    if kind == "slash":
        d.polygon([(10,76),(26,43),(64,17),(86,10),(60,33),(34,55)], fill=p[1])
        d.polygon([(14,73),(35,46),(83,12),(57,37),(33,57)], fill=p[4])
        line([(24,62),(41,43),(78,17)], p[5], 2)
    elif kind == "fist":
        d.polygon([(23,40),(29,29),(58,24),(74,35),(75,59),(64,71),(37,70),(23,58)], fill=p[0])
        d.polygon([(27,41),(33,33),(60,28),(69,37),(70,58),(61,65),(38,65),(28,55)], fill=p[2])
        for x, y in [(31,38),(41,34),(51,32),(61,35)]:
            d.rectangle((x,y,x+8,y+17), fill=p[3], outline=p[1])
            line([(x+1,y+2),(x+6,y+2)], p[5], 2)
        d.polygon([(28,51),(40,45),(54,49),(56,56),(42,62),(31,59)], fill=p[3], outline=p[1])
        line([(33,53),(43,50),(50,52)], p[4], 2)
        line([(57,58),(64,56),(65,46)], p[1], 2)
    elif kind == "hand":
        d.polygon([(35,72),(31,47),(34,40),(29,21),(33,17),(42,38),(40,13),(45,10),
                   (49,36),(51,14),(56,14),(57,39),(63,23),(68,25),(63,47),
                   (75,39),(79,43),(64,62),(60,76)], fill=p[2], outline=p[0])
        line([(38,69),(37,48),(45,45),(51,55),(58,45),(64,43)],p[4],2)
        line([(46,61),(54,64),(55,72)],p[1],2)
        line([(43,19),(46,38)],p[5])
    elif kind == "jaw":
        for side in (-1, 1):
            y = 48+side*19
            d.polygon([(15,y),(28,y+side*8),(67,y+side*8),(81,y),(65,y-side*8),(30,y-side*8)], fill=p[2], outline=p[0])
            for x in range(24,78,11):
                d.polygon([(x,y),(x+7,y),(x+3,y-side*14)], fill=p[4], outline=p[1])
    elif kind in ("shard", "blade"):
        pts = [(7,55),(62,32),(88,44),(65,58)] if kind == "shard" else [(8,57),(22,43),(89,35),(73,50),(25,58)]
        d.polygon(pts, fill=p[2], outline=p[0])
        line([pts[0],pts[1],pts[2]],p[5],2)
        line([pts[0],pts[-1],pts[2]],p[3],2)
    elif kind == "ring":
        ellipse(48,48,35,30,p[1],5)
        ellipse(48,48,33,29,p[4],2)
        for k in range(8):
            a = k*math.tau/8 + phase*0.3
            line([(48+math.cos(a)*30,48+math.sin(a)*26),
                  (48+math.cos(a)*37,48+math.sin(a)*32)], p[5], 2)
    elif kind == "burst":
        pts=[]
        for k in range(24):
            a=k*math.tau/24
            r=(35+(k*7)%9) if k%2==0 else 16+(k*3)%9
            pts.append(point(48+math.cos(a)*r,48+math.sin(a)*r))
        d.polygon(pts,fill=p[2],outline=p[0])
        ellipse(48,48,19,17,p[4])
        ellipse(48,48,12,10,p[5])
    elif kind == "lightning":
        pts=[(9,55),(25,46),(33,52),(44,30),(48,62),(61,40),(69,48),(88,37)]
        line(pts,p[0],7)
        line(pts,p[3],4)
        line(pts,p[5],1)
    elif kind == "beam":
        for y, w, color in [(48,19,p[0]),(48,13,p[2]),(48,7,p[3]),(48,2,p[5])]:
            line([(0,y),(95,y)],color,w)
        for k in range(6):
            x=(k*19+int(phase*80))%112-8
            line([(x-12,40),(x,45),(x+9,48),(x,51),(x-12,56)],p[4],2)
    elif kind in ("orb", "droplet", "seed"):
        if kind == "droplet":
            d.polygon([(48,13),(27,48),(25,64),(39,77),(58,76),(71,62),(65,42)], fill=p[1])
            ellipse(47,58,17,18,p[3])
            ellipse(40,52,5,9,p[5])
        elif kind == "seed":
            ellipse(48,48,20,28,p[1])
            ellipse(47,46,16,24,p[3])
            line([(47,22),(42,42),(48,54),(43,70)],p[5],2)
            for y in [30,42,54]:
                line([(50,y),(62,y-4)],p[1],2)
        else:
            ellipse(48,48,25,25,p[0])
            ellipse(47,47,21,22,p[2])
            ellipse(45,43,15,15,p[3])
            ellipse(39,37,6,5,p[5])
            line([(52,65),(63,59),(67,50)],p[4],2)
    elif kind == "shield":
        d.polygon([(17,26),(48,15),(79,26),(73,58),(48,82),(23,58)], fill=p[0], outline=p[4])
        d.polygon([(23,30),(48,22),(72,30),(67,55),(48,74),(29,55)], fill=p[1], outline=p[2])
        line([(48,23),(48,72)],p[4],2)
        line([(27,36),(43,46),(33,58)],p[3],2)
        line([(68,36),(53,46),(63,58)],p[3],2)
    elif kind == "plate":
        d.polygon([(29,13),(58,17),(68,36),(64,72),(47,84),(29,65)], fill=p[1], outline=p[4])
        d.polygon([(35,21),(54,24),(59,39),(55,67),(47,73),(36,61)],fill=p[2])
        line([(37,25),(37,58),(46,66)],p[5],2)
    elif kind == "foot":
        d.polygon([(30,17),(58,14),(64,31),(58,51),(69,69),(58,81),(28,80),(26,64),(36,46)], fill=p[1], outline=p[0])
        d.polygon([(35,20),(53,19),(56,33),(50,53),(59,68),(51,73),(34,73),(32,65),(41,47)],fill=p[3])
        line([(37,22),(39,39)],p[5],2)
        line([(32,67),(53,65)],p[1],2)
    elif kind == "eye":
        d.polygon([(11,48),(31,30),(62,29),(85,48),(63,66),(32,65)],fill=p[0],outline=p[3])
        line([(15,48),(33,34),(61,33),(80,48)],p[5],2)
        ellipse(48,48,9,16,p[3])
        ellipse(48,48,3,12,p[0])
        d.rectangle((47,39,49,43),fill=p[5])
    elif kind == "root":
        growth = min(1, .25+phase*2)
        pts = [(18,86),(25,72),(20,61),(37,47),(40,30),(64,9)]
        pts = [(x,86+(y-86)*growth) for x,y in pts]
        line(pts,p[0],11)
        line(pts,p[2],8)
        line([(x-2,y-1) for x,y in pts],p[4],2)
        for x,y in pts[1:-1]:
            line([(x,y),(x+14,y-2),(x+22,y-14)],p[1],5)
            line([(x,y-1),(x+14,y-4),(x+21,y-14)],p[3],2)
    elif kind == "wood":
        ellipse(48,48,29,29,p[0])
        ellipse(48,47,26,26,p[2])
        for r in [6,13,22]:
            ellipse(47,46,r,r,p[4],2)
        line([(49,19),(51,30),(44,39),(50,48),(44,71)],p[0],2)
    elif kind == "flower":
        for k in range(7):
            a=k*math.tau/7+phase*.4
            x,y=48+math.cos(a)*17,48+math.sin(a)*17
            ellipse(x,y,12,12,p[1])
            ellipse(x-1,y-2,9,9,p[3])
            line([(48,48),(x,y)],p[4],2)
        ellipse(48,48,9,9,COLORS["leaf"][2])
        ellipse(46,46,5,5,COLORS["leaf"][5])
    elif kind == "human":
        ellipse(47,22,9,11,p[2])
        d.polygon([(42,31),(34,36),(26,54),(17,58),(19,64),(33,62),(41,48),
                   (37,68),(29,83),(38,86),(48,68),(57,85),(66,82),(59,61),
                   (57,46),(67,57),(79,59),(81,52),(70,50),(60,33)],fill=p[2],outline=p[0])
        line([(44,35),(46,56),(41,64)],p[4],3)
        line([(43,21),(46,23),(50,20)],p[0],2)
        line([(42,48),(51,46)],p[0],2)
    elif kind == "drill":
        d.polygon([(13,26),(35,26),(88,48),(35,70),(13,70)],fill=p[1],outline=p[0])
        for k in range(5):
            x=17+k*12
            r=21-k*3.5
            line([(x,48-r),(x+10,48),(x,48+r)],p[4],3)
            line([(x+4,48-r+2),(x+13,48)],p[2],2)
        line([(26,29),(86,48)],p[5],1)
    elif kind == "wing":
        d.polygon([(19,78),(23,48),(34,26),(73,12),(88,15),(64,31),(82,27),
                   (60,43),(74,41),(53,56),(63,56),(43,68)],fill=p[2],outline=p[0])
        line([(23,68),(33,40),(65,23)],p[5],2)
        for k in range(4):
            line([(28+k*4,60-k*8),(60+k*5,35-k*5)],p[4],2)
    elif kind == "stitch":
        line([(15,53),(33,44),(50,51),(68,43),(82,48)],p[1],3)
        for x in range(21,80,9):
            y=48+math.sin(x)*3
            line([(x-2,y-5),(x+2,y+5)],p[4],2)
    elif kind == "star":
        d.polygon([(48,22),(52,43),(73,48),(52,52),(48,74),(44,52),(23,48),(44,44)],fill=p[5])
        d.rectangle((45,45,51,51),fill=p[3])
    else:
        raise ValueError(f"Unknown art primitive: {kind}")
    return image


def interpolate(value, t):
    return value[0]+(value[1]-value[0])*t if isinstance(value,list) else value


def render(spec, frame):
    count=spec.get("frames",24)
    t=(frame % count)/(count-1)
    contact=frame//count
    image=Image.new("RGBA",(96,96))
    for row in spec["art"]:
        start,end=row.get("time",[0,1])
        if t <= start or t >= end:
            continue
        q=(t-start)/(end-start)
        alpha=min(1,q/.15,(1-q)/.22)*row.get("alpha",1)
        cel=primitive(row["kind"],row.get("palette",spec.get("palette","curse")),q)
        scale=max(.02,interpolate(row.get("scale",1),q))
        cel=cel.resize((max(1,round(96*scale)),)*2,Image.Resampling.NEAREST)
        angle=interpolate(row.get("rotate",0),q)+contact*row.get("contactRotation",0)
        cel=cel.rotate(angle,Image.Resampling.NEAREST,expand=True)
        if row.get("flip",False):
            cel=cel.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        cel.putalpha(cel.getchannel("A").point(lambda a: round(a*alpha)))
        x=interpolate(row.get("x",48),q)
        y=interpolate(row.get("y",48),q)
        repetitions=row.get("repeat",1)
        for k in range(repetitions):
            a=(k/repetitions)*math.tau+q*row.get("orbit",0)
            radius=interpolate(row.get("radius",0),q)
            instance=cel.rotate(-math.degrees(a),Image.Resampling.NEAREST,expand=True) if row.get("radial") else cel
            image.alpha_composite(instance,(round(x+math.cos(a)*radius-instance.width/2),
                                           round(y+math.sin(a)*radius-instance.height/2)))
    return image.resize((192,192),Image.Resampling.NEAREST)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination",type=Path,default=ROOT/"graphics/src/main/resources/assets/animations")
    parser.add_argument("--review",type=Path,default=ROOT/"docs/animations/cursed-spirits")
    parser.add_argument("--check",action="store_true",help="validate current spirit binding coverage without writing")
    args=parser.parse_args()
    specs=json.loads(ART.read_text(encoding="utf-8"))
    ids=[s["id"] for s in specs]
    bindings=[m for s in specs for m in s.get("moveIds",[])]
    assert len(ids)==len(set(ids)),"Duplicate effect IDs"
    assert len(bindings)==len(set(bindings)),"Duplicate move bindings"
    moves=json.loads((ROOT/"data/moves/all_moves.json").read_text(encoding="utf-8"))
    characters=json.loads((ROOT/"data/characters/all_characters.json").read_text(encoding="utf-8"))
    techniques={c["innateTechniqueName"] for c in characters
                if c.get("type")=="CURSED_SPIRIT" and c.get("innateTechniqueName")}
    spirit_ids={m["id"] for m in moves if "CURSED_SPIRIT" in (m.get("moveTypes") or [])
                or m.get("requiredTechniqueId") in techniques}
    # Coverage follows current authored types/techniques, never fixed AP/stats/effect values.
    assert spirit_ids,"No cursed spirit content found"
    assert spirit_ids <= set(bindings),f"Missing spirit animations: {sorted(spirit_ids-set(bindings))}"
    assert set(bindings) <= {m["id"] for m in moves},"Unknown move binding"
    if args.check:
        manifest=json.loads((args.destination/"cursed-spirits/manifest.json").read_text(encoding="utf-8"))
        assert {e["id"] for e in manifest["effects"]}==set(ids)
        assert {m for e in manifest["effects"] for m in e.get("moveIds",[])}==set(bindings)
        hashes=[]
        for e in manifest["effects"]:
            path=args.destination/"cursed-spirits"/e["sheet"]
            with Image.open(path) as sheet:
                assert sheet.mode=="RGBA" and sheet.width==1152
                assert sheet.height==192*math.ceil(e["frameCount"]/6)
                assert sheet.getchannel("A").getextrema()==(0,255),e["id"]
            hashes.append(hashlib.sha256(path.read_bytes()).hexdigest())
        assert len(set(hashes))==len(hashes),"Duplicate art sheets"
        print(f"Validated {len(bindings)} unique move bindings and {len(hashes)} distinct RGBA sheets.")
        return
    pack=args.destination/"cursed-spirits"
    (pack/"sprites").mkdir(parents=True,exist_ok=True)
    args.review.mkdir(parents=True,exist_ok=True)
    manifest=dict(schemaVersion=1,sheetOrder="row-major-top-left",frameWidth=192,frameHeight=192,columns=6,effects=[])
    choreography_path=args.destination/"choreography.json"
    choreography=json.loads(choreography_path.read_text(encoding="utf-8")) if choreography_path.exists() else {"schemaVersion":1,"profiles":{},"effects":{}}
    boards={}
    for s in specs:
        n=s.get("frames",24)*s.get("contacts",1)
        frames=[render(s,i) for i in range(n)]
        sheet=Image.new("RGBA",(1152,192*math.ceil(n/6)))
        for i,f in enumerate(frames):
            sheet.paste(f,((i%6)*192,(i//6)*192))
        sheet.save(pack/"sprites"/(s["id"]+".png"),optimize=True)
        e={k:s[k] for k in ["id","name","placement","role","moveIds","description"] if k in s}
        e.update(sheet="sprites/"+s["id"]+".png",frameCount=n,frameDurationMs=s.get("frameDurationMs",40),loop=False,
                 anchor=[.5,.5],impactFrames=[s.get("impact",10)+i*s.get("frames",24) for i in range(s.get("contacts",1))],sourceGraphics=[])
        manifest["effects"].append(e)
        if "profile" in s:
            choreography["effects"][s["id"]]=s["id"]
            choreography["profiles"][s["id"]]=s["profile"]
        if s.get("moveIds"):
            boards.setdefault(s["family"],[]).append((s,frames))
    (pack/"manifest.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
    choreography_path.write_text(json.dumps(choreography,indent=2)+"\n",encoding="utf-8")
    font=ImageFont.load_default(size=14)
    small=ImageFont.load_default(size=11)
    for family,entries in boards.items():
        board=Image.new("RGB",(960,58+len(entries)*150),"#101720")
        d=ImageDraw.Draw(board)
        d.text((18,14),family.upper()+" / ANTICIPATION - CONTACT - RELEASE",fill="#ddedd9",font=font)
        for i,(s,frames) in enumerate(entries):
            y=58+i*150
            d.rectangle((12,y,947,y+142),fill="#1a2530",outline="#344752")
            d.text((22,y+14),s["moveIds"][0]+"  "+s["name"],fill="#eff5e3",font=font)
            d.text((22,y+39),s["placement"].upper()+" / "+s["role"],fill="#8db8ac",font=small)
            desc=s["description"]
            words=desc.split(); lines=[""]
            for word in words:
                if len(lines[-1])+len(word)>48: lines.append("")
                lines[-1]+=(" " if lines[-1] else "")+word
            for j,line in enumerate(lines): d.text((22,y+62+j*15),line,fill="#b1bfcb",font=small)
            for j,idx in enumerate([5,s.get("impact",10),18]):
                tile=frames[min(idx,len(frames)-1)].resize((144,144),Image.Resampling.NEAREST)
                board.paste(tile,(480+j*154,y),tile)
        board.save(args.review/(family+".png"),optimize=True)
    print(f"Exported {len(bindings)} move animations, {len(specs)-len(bindings)} composable layers, and {len(boards)} review boards.")


if __name__ == "__main__":
    main()
