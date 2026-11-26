import json,re
d=json.load(open('idx.json'));p=d['packages']
G={ 'ألعاب':['Puzzle Game','Game Helper','Board Game','Casual Game','Educational Game','Action Game','Strategy Game','Card Game','Dice','Role-Playing Game','Word Game','Party Game','Shooter Game','Sport Game','Platformer Game','Emulator','Visual Novel'],
'تواصل ومراسلة':['Messaging','Voice & Video Chat','Social Network','Forum','Email','Phone & SMS','Contact','Push'],
'إنترنت ومتصفحات':['Internet','Browser','Download','Bookmark','News'],
'أمان وخصوصية':['Security','Password & 2FA','VPN & Proxy','File Encryption & Vault','Text Encryption','Firewall','DNS & Hosts'],
'صوت وفيديو وموسيقى':['Multimedia','Online Media Player','Local Media Player','Podcast','Radio','Audiobook','Lyrics','Music Practice Tool','Ambient Sound','Recorder','Cast'],
'صور وتصميم':['Graphics','Camera','Gallery','Draw','Wallpaper','Icon Pack','Theming','OCR'],
'قراءة وكتابة':['Reading','Writing','Ebook Reader','Note','Text Editor','Translation & Dictionary','Text to Speech','Speech Recognizer'],
'تعليم وعلوم':['Science & Education'],
'دين':['Religion'],
'إنتاجية ومهام':['Task','Calendar & Agenda','Timer','Schedule','Habit Tracker','Time Tracker','Alarm Clock','Clock','Stopwatch','Shopping List','Inventory','Digital Wellbeing'],
'مال وتسوق':['Finance Manager','Market & Price','Wallet','Pass Wallet','Recipe Manager'],
'صحة ورياضة':['Sports & Health','Workout','Health Manager','Diet','Medication','Meditation','Mental Health'],
'خرائط وسفر':['Navigation','Public Transport','Location Tracker & Sharer','Weather','Emergency Action'],
'أدوات النظام':['System','Launcher','Keyboard & IME','App Manager','File Manager','Battery','Flashlight','Volume','Notification','Calculator','Unit Convertor','App Store & Updater','Theming'],
'شبكات وملفات':['Connectivity','Cloud Storage & File Sync','File Transfer','Remote Controller','Remote Access','Network Analyzer'],
'برمجة':['Development','Code & Forge'],
'ذكاء اصطناعي':['AI Chat']}
rev={}
for g,l in G.items():
    for c in l: rev.setdefault(c,g)
names=list(G)+['أخرى']
bad={'NonFreeAssets','NonFreeDep','NonFreeAdd','UpstreamNonFree'}
def L(d,*k):
    if not d: return ''
    for x in k:
        if x in d: return d[x]
    return next(iter(d.values()))
out={};ab={'arm64-v8a','armeabi-v7a'}
for pk,x in p.items():
    m=x['metadata'];best=None
    for v in x['versions'].values():
        mf=v['manifest']
        if bad&set(v.get('antiFeatures',{})): continue
        nc=mf.get('nativecode')
        if nc and not (ab&set(nc)): continue
        if best is None or mf['versionCode']>best['manifest']['versionCode']: best=v
    if not best: continue
    mf=best['manifest']
    gs=sorted({names.index(rev.get(c,'أخرى')) for c in m.get('categories',[])}) or [len(names)-1]
    ic=L(m.get('icon'),'ar','en-US')
    ic=ic['name'] if ic else ''
    af=','.join(sorted(set(best.get('antiFeatures',{}))&{'Ads','Tracking','NonFreeNet','KnownVuln','NoSourceSince','TetheredNet'}))
    nc=mf.get('nativecode') or []
    f=[pk,L(m.get('name'),'ar','en-US'),L(m.get('summary'),'ar','en-US'),','.join(map(str,gs)),m.get('license',''),best['file']['name'].lstrip('/'),best['file']['sha256'],str(best['file']['size']),mf.get('versionName',''),str(mf['versionCode']),str(m.get('added',0)//1000),str(m.get('lastUpdated',0)//1000),str(mf.get('usesSdk',{}).get('minSdkVersion',1)),ic,af,m.get('webSite') or '',m.get('sourceCode') or '',('1' if 'arm64-v8a' in nc else '0') if nc else '',('1' if 'armeabi-v7a' in nc else '0') if nc else '']
    ds=L(m.get('description'),'ar','en-US'); ds=re.sub(r'<[^>]+>',' ',ds or ''); ds=re.sub(r'\s+',' ',ds).strip()[:320]
    f+=['0',ds]
    out[pk]=f

iz=json.load(open('izzy.json'))
FREE=re.compile(r'GPL|MIT|Apache|BSD|MPL|ISC|Unlicense|CC0|CC-BY|EUPL|EPL|Zlib|WTFPL|PublicDomain|Artistic|CECILL|AFL|0BSD|X11')
added=0
for a in iz['apps']:
    pk=a['packageName']; lic=a.get('license','')
    if not FREE.search(lic) or 'NonCommercial' in lic or '-NC' in lic: continue
    vs=[v for v in iz['packages'].get(pk,[]) if v.get('hashType','sha256')=='sha256']
    best=None
    for v in vs:
        nc=v.get('nativecode')
        if nc and not (ab&set(nc)): continue
        if bad&set(v.get('antiFeatures',[])): continue
        if best is None or v['versionCode']>best['versionCode']: best=v
    if not best: continue
    if pk in out and int(out[pk][9])>=best['versionCode']: continue
    loc=a.get('localized',{}); l=loc.get('ar') or loc.get('en-US') or (next(iter(loc.values())) if loc else {})
    en=loc.get('en-US',{})
    name=l.get('name') or en.get('name') or a.get('name') or pk
    sm=l.get('summary') or en.get('summary') or a.get('summary') or ''
    ds=l.get('description') or en.get('description') or a.get('description') or ''
    ds=re.sub(r'<[^>]+>',' ',ds); ds=re.sub(r'\s+',' ',ds).strip()[:320]
    gs=sorted({names.index(rev.get(c,'أخرى')) for c in a.get('categories',[])}) or [len(names)-1]
    nc=best.get('nativecode') or []
    af=','.join(sorted(set(best.get('antiFeatures',[]))&{'Ads','Tracking','NonFreeNet','KnownVuln','NoSourceSince','TetheredNet'}))
    f=[pk,name,sm,','.join(map(str,gs)),lic,best['apkName'],best['hash'],str(best['size']),best.get('versionName',''),str(best['versionCode']),str(a.get('added',0)//1000),str(a.get('lastUpdated',0)//1000),str(best.get('minSdkVersion',1)),'',af,a.get('webSite') or '',a.get('sourceCode') or '',('1' if 'arm64-v8a' in nc else '0') if nc else '',('1' if 'armeabi-v7a' in nc else '0') if nc else '','1',ds]
    out[pk]=[x.replace('\t',' ').replace('\n',' ') for x in f]; added+=1
print('izzy added/replaced',added)
out=['\t'.join(re.sub(r'[\t\r\n\u2028\u2029\x0b\x0c\x85]',' ',x) for x in v) for v in out.values()]
open('app/assets/catalog.tsv','w').write('\n'.join(out))
open('app/assets/cats.txt','w').write('\n'.join(names))
print(len(out))
