const B='http://127.0.0.1:9000', NS='demo-facil-default-rtdb';
const b64=o=>Buffer.from(JSON.stringify(o)).toString('base64url');
const tok=u=>b64({alg:'none',typ:'JWT'})+'.'+b64({iat:Math.floor(Date.now()/1000),exp:Math.floor(Date.now()/1000)+3600,iss:'https://securetoken.google.com/demo-facil',aud:'demo-facil',sub:u,user_id:u,auth_time:Math.floor(Date.now()/1000),firebase:{sign_in_provider:'anonymous',identities:{}}})+'.';
async function call(u,m,p,body){const r=await fetch(`${B}/${p}.json?ns=${NS}&auth=${u?tok(u):'x'}`,{method:m,body:body===undefined?undefined:JSON.stringify(body)});
  let t=await r.text();return {s:r.status,t};}
let fails=0;
const ok=(name,r,want)=>{const pass=want?(r.s===200):(r.s===401||r.s===403);if(!pass)fails++;console.log((pass?'PASS':'FAIL')+' '+name+' -> '+r.s+(pass?'':' '+r.t));};
const SEN='senior00000A', FAM='family00000B', EVIL='evil000000C', FAM2='family0000D2';
const SV={'.sv':'timestamp'};
(async()=>{
 // Senior crea su dispositivo
 ok('senior crea owner',await call(SEN,'PUT',`devices/${SEN}/owner`,SEN),1);
 ok('senior pone nombre',await call(SEN,'PUT',`devices/${SEN}/name`,'Móvil de Carmen'),1);
 ok('otro NO puede crear owner ajeno',await call(EVIL,'PUT',`devices/${SEN}/owner`,EVIL),0);
 ok('senior config',await call(SEN,'PUT',`devices/${SEN}/config`,{json:'{}',rev:1,by:'x'}),1);
 ok('config con campo extra rechazado',await call(SEN,'PUT',`devices/${SEN}/config`,{json:'{}',rev:1,by:'x',evil:1}),0);
 ok('status senior',await call(SEN,'PUT',`devices/${SEN}/status`,{battery:50,charging:false,lastActive:1,sos:0,lastSeen:SV,done:{}}),1);
 // Pairing
 const CODE='ABCDEFGHJK', exp=Date.now()+600000;
 ok('senior crea código',await call(SEN,'PUT',`codes/${CODE}`,{dev:SEN,expiresAt:exp}),1);
 ok('senior pairing',await call(SEN,'PUT',`devices/${SEN}/pairing`,{code:CODE,expiresAt:exp}),1);
 ok('código con dev ajeno rechazado',await call(EVIL,'PUT',`codes/KLMNPQRSTU`,{dev:SEN,expiresAt:exp}),0);
 ok('código formato inválido',await call(EVIL,'PUT',`codes/abc`,{dev:EVIL,expiresAt:exp}),0);
 ok('código caducidad lejana rechazada',await call(EVIL,'PUT',`codes/ZZZZZZZZZZ`,{dev:EVIL,expiresAt:Date.now()+99999999}),0);
 ok('familiar lee código',await call(FAM,'GET',`codes/${CODE}`),1);
 ok('NO se puede listar codes',await call(FAM,'GET',`codes`),0);
 ok('NO se puede listar devices',await call(FAM,'GET',`devices`),0);
 // Familiar pide unirse
 ok('familiar sin código válido NO se une',await call(EVIL,'PUT',`devices/${SEN}/members/${EVIL}`,{name:'x',status:'pending',code:'WRONGCODE2',createdAt:SV}),0);
 ok('familiar se une pending',await call(FAM,'PUT',`devices/${SEN}/members/${FAM}`,{name:'María',status:'pending',code:CODE,createdAt:SV}),1);
 ok('familiar NO puede auto-activarse',await call(FAM,'PUT',`devices/${SEN}/members/${FAM}/status`,'active'),0);
 ok('familiar NO se une directo como active',await call(FAM2,'PUT',`devices/${SEN}/members/${FAM2}`,{name:'x',status:'active',code:CODE,createdAt:SV}),0);
 ok('pending NO lee dispositivo',await call(FAM,'GET',`devices/${SEN}`),0);
 ok('pending NO lee config',await call(FAM,'GET',`devices/${SEN}/config`),0);
 ok('pending NO escribe config',await call(FAM,'PUT',`devices/${SEN}/config`,{json:'{}',rev:9}),0);
 ok('pending lee su propio estado',await call(FAM,'GET',`devices/${SEN}/members/${FAM}/status`),1);
 ok('ajeno NO aprueba',await call(EVIL,'PUT',`devices/${SEN}/members/${FAM}/status`,'active'),0);
 ok('senior aprueba',await call(SEN,'PUT',`devices/${SEN}/members/${FAM}/status`,'active'),1);
 ok('activo lee dispositivo',await call(FAM,'GET',`devices/${SEN}`),1);
 ok('activo escribe config',await call(FAM,'PUT',`devices/${SEN}/config`,{json:'{"a":1}',rev:2,by:'María'}),1);
 ok('activo NO escribe status',await call(FAM,'PUT',`devices/${SEN}/status/battery`,1),0);
 ok('activo NO escribe name',await call(FAM,'PUT',`devices/${SEN}/name`,'hack'),0);
 ok('activo NO añade otro miembro',await call(FAM,'PUT',`devices/${SEN}/members/${EVIL}`,{name:'e',status:'active'}),0);
 ok('activo NO borra el dispositivo',await call(FAM,'DELETE',`devices/${SEN}`),0);
 ok('activo NO escribe owner',await call(FAM,'PUT',`devices/${SEN}/owner`,FAM),0);
 ok('ajeno NO lee dispositivo',await call(EVIL,'GET',`devices/${SEN}`),0);
 ok('sin autenticar NO lee',await call(null,'GET',`devices/${SEN}/name`),0);
 ok('familiar vincula su lista',await call(FAM,'PUT',`users/${FAM}/devices/${SEN}`,true),1);
 ok('ajeno NO toca users de otro',await call(EVIL,'PUT',`users/${FAM}/devices/${EVIL}`,true),0);
 ok('ajeno NO lee users de otro',await call(EVIL,'GET',`users/${FAM}`),0);
 ok('senior quita a familiar',await call(SEN,'DELETE',`devices/${SEN}/members/${FAM}`),1);
 ok('ex-miembro ya NO lee',await call(FAM,'GET',`devices/${SEN}`),0);
 ok('ex-miembro NO escribe config',await call(FAM,'PUT',`devices/${SEN}/config`,{json:'{}',rev:99}),0);
 ok('senior borra todo',await call(SEN,'DELETE',`devices/${SEN}`),1);
 console.log(fails?`\n${fails} FALLOS`:'\nTODAS LAS REGLAS OK');process.exit(fails?1:0);
})();
