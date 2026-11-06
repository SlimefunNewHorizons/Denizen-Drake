The Denizen Scripting Language - Spigot Impl
--------------------------------------------

An implementation of the Denizen Scripting Language for Spigot servers, with strong Citizens interlinks to emphasize the power of using Denizen with NPCs!

**Version 1.3.4**: Compatible with Spigot 1.17.1, 1.18.2, 1.19.4, 1.20.6, 1.21.11, 26.1.2, 26.2, and 26.3!

### Estado del fork DrakesCraft (#676)

- El perfil `runtime-26.2` construye `plugin`, `paper`, `v26_2` y `dist` con
  JDK 25, API Spigot 26.2 y Paper 26.2.build.129-stable. Produce una distribucion
  completa para 26.2; el build multiversion original permanece como predeterminado.
- El pegado aborta al primer fallo: cancela la tarea, restaura la bandera de
  fisica anterior y libera el lector/espera sin pegar entidades del callback de exito.
- No hay rollback de bloques ya colocados ni se omite el NBT que falla.
- La distribucion arranco en Purpur 26.2-138 con Java 25. Once regresiones
  unitarias pasan; el ensayo en servidor sigue **rojo**: 44 NPE al aplicar NBT
  antiguo a camas y una comparacion de items de cofre fallida, aun inconclusa.
  PDC sintetico y cancelacion del pegado si pasan. No esta instalado en Dallas
  ni validado en 1.21.11. La lista anterior corresponde a upstream.
- Causa reproducida: en 26.2 las camas ya no son entidades de bloque
  ([Paper](https://papermc.io/news/26-2/)). Omitir todo su NBT perderia posibles
  datos personalizados: hace falta una migracion que los conserve.

**Learn about Denizen from the Beginner's guide:** https://guide.denizenscript.com/guides/background/index.html

#### Download Links:

- **Release builds**: https://ci.citizensnpcs.co/job/Denizen/
- **Developmental builds**: https://ci.citizensnpcs.co/job/Denizen_Developmental/
- **SpigotMC - VERY SLOW releases**: https://www.spigotmc.org/resources/denizen.21039/

#### Need help using Denizen? Try one of these places:

- **Discord** - chat room (Modern, strongly recommended): https://discord.gg/Q6pZGSR
- **Denizen Home Page** - a link directory (Modern): https://denizenscript.com/
- **Forum and script sharing** (Modern): https://forum.denizenscript.com/
- **Meta Documentation** - command/tag/event/etc. search (Modern): https://meta.denizenscript.com/
- **Beginner's Guide** - text form (Modern): https://guide.denizenscript.com/

#### Also check out:

- **Citizens2 (NPC support)**: https://github.com/CitizensDev/Citizens2/
- **Depenizen (Other plugin support)**: https://github.com/DenizenScript/Depenizen
- **dDiscordBot (Adds a Discord bot to Denizen)**: https://github.com/DenizenScript/dDiscordBot
- **DenizenCore (Our core, needed for building)**: https://github.com/DenizenScript/Denizen-Core
- **DenizenVSCode (extension for writing Denizen scripts in VS Code)**: https://github.com/DenizenScript/DenizenVSCode

### Building

- Built against JDK 17, using maven `pom.xml` as project file.
- Requires building all listed versions of Spigot via Spigot BuildTools: https://www.spigotmc.org/wiki/buildtools/

For the isolated 26.2 distribution, install **Spigot 26.2** with BuildTools first,
then run with JDK 25 (the other Spigot server versions are not needed):

```sh
mvn -Pruntime-26.2 -DBUILD_NUMBER=676 -DBUILD_CLASS=CUSTOM clean package
```

On Star, wrap that command in `~/ai-hub/scripts/build_seguro.sh 25 "$PWD"`.
The output is `target/Denizen-1.3.4-b676-CUSTOM.jar`, including DenizenCore,
the Paper integration and NMS 26.2. Its manifest declares Mojang mappings and
`Denizen-Runtime: 26.2`. This profile is **26.2 only**, not a universal runtime.
The default build still includes all eight upstream NMS modules/dependencies.

`plugin/src/test/server/DenizenBlockNbtSmoke.java` is a separate test plugin,
compiled against the shaded distribution and the server API/libraries. Package
it with its adjacent `plugin.yml`; never include it in the gameplay jar.
It refuses to run unless the server binds to `127.0.0.1`, has no players and
contains only the disposable world `smoke676`. It checks chest items/PDC and
cancellation of a deliberately invalid delayed paste, then shuts down the test
server. Optional local `.schem` copies in `fixtures/` exercise v2 block entities;
Sponge v3 is counted as skipped, not reported as supported. No schematics or
player data are distributed in this repository.

### Maven

```xml
    <repository>
        <id>citizens-repo</id>
        <url>https://maven.citizensnpcs.co/repo</url>
    </repository>
    <dependencies>
        <dependency>
            <groupId>com.denizenscript</groupId>
            <artifactId>denizen</artifactId>
            <version>1.3.4-SNAPSHOT</version>
            <type>jar</type>
            <scope>provided</scope>
            <exclusions>
                <exclusion>
                    <groupId>*</groupId>
                    <artifactId>*</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>
```

### Licensing pre-note:

This is an open source project, provided entirely freely, for everyone to use and contribute to.

If you make any changes that could benefit the community as a whole, please contribute upstream.

### The short of the license is:

You can do basically whatever you want, except you may not hold any developer liable for what you do with the software.

### Previous License

Copyright (C) 2012-2013 Aufdemrand, All Rights Reserved.

Copyright (C) 2013-2019 The Denizen Script Team, All Rights Reserved.

### The long version of the license follows:

The MIT License (MIT)

Copyright (c) 2019-2026 The Denizen Script Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
