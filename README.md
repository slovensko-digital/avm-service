# AVM (Autogram v mobile) Service

Java microservice slúžiaci na výrobu elektronických podpisov podľa eIDAS, vizualizáciu dokumentov a v budúcnosti aj na overovanie podpisov. Zdrojový kód tohto microservicu je z veľkej časti prebratý z projektu [Autogram](https://sluzby.slovensko.digital/autogram/) s EUPL v1.2 licenciou, ktorého autormi sú Jakub Ďuraš, Solver IT s.r.o., Slovensko.Digital, CRYSTAL CONSULTING, s.r.o. a ďalší spoluautori.

Ide o súčasť riešenia [Autogram v mobile](https://sluzby.slovensko.digital/autogram-v-mobile/), ktoré vytvorili freevision s.r.o., Služby Slovensko.Digital s.r.o. a dobrovoľníci pod EUPL v1.2 licenciou. Prevádzkovateľom je Služby Slovensko.Digital s.r.o.. Prípadné issues riešime v [GitHub projekte](https://github.com/orgs/slovensko-digital/projects/5) alebo rovno v tomto repozitári.

Projekt sa skladá z viacerých častí:
- **Server**
  - [AVM server](https://github.com/slovensko-digital/avm-server) - Ruby on Rails API server poskytujúci funkcionalitu zdieľania a podpisovania dokumentov.
  - 👉 [AVM service](https://github.com/slovensko-digital/avm-service) - Java microservice využívajúci Digital Signature Service knižnicu pre elektronické podpisovanie a generovanie vizualizácie dokumentov.
- **Mobilná aplikácia**
  - [AVM app Flutter](https://github.com/slovensko-digital/avm-app-flutter) - Flutter aplikácia pre iOS a Android.
  - [AVM client Dart](https://github.com/slovensko-digital/avm-client-dart) - Dart API klient pre komunikáciu s AVM serverom.
  - [eID mSDK Flutter](https://github.com/slovensko-digital/eidmsdk-flutter) - Flutter wrapper "štátneho" [eID mSDK](https://github.com/eIDmSDK) pre komunikáciu s občianskym preukazom.
- [**Autogram extension**](https://github.com/slovensko-digital/autogram-extension) - Rozšírenie do prehliadača, ktoré umožňuje podpisovanie priamo na štátnych portáloch.

## Ako si to rozbehnúť

Ide o Java projekt. Nepoužívajte `maven`, namiesto toho je v repozitári skript `mvnw`. Po naklonovaní projektu je potrebné zavolať:
```
./mvnw initialize
```
Potrebná verzia Javy sa potom nachádza niekde v adresári `target`. Odporúčame projekt spúšťať cez IntelliJ.

### Docker

```
docker build -t avm .
docker run -p8720:8720 avm
```

