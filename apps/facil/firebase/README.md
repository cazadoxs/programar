# Reglas de Firebase

`database.rules.json` son las reglas de Realtime Database (publícalas en la consola).

`rules-test.js` las prueba contra el emulador (39 comprobaciones: nadie lee ni escribe lo de otro,
un familiar pendiente no ve nada, un ex-miembro pierde el acceso, no se pueden listar códigos…):

```
npm i -g firebase-tools
# en una carpeta con firebase.json que apunte a database.rules.json:
firebase emulators:start --only database --project demo-facil &
node rules-test.js
```
