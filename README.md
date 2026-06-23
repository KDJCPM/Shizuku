# ShizukuREADME.md 2.0

## Background

When developing apps that requires root, the most common method is to run some commands in the su shell. For example, there is an app that uses the `pm enable/disable` command to enable/disable components.

This method has very big disadvantages:

1. **Extremely slow** (Multiple process creation)
2. Needs to process texts (**Super unreliable**)
3. The possibility is limited to available commands
4. Even if ADB has sufficient permissions, the app requires root privileges to run

Shizuku uses a completely different way. See detailed description below.

## User guide & Download

<https://shizuku.rikka.app/>

## How does Shizuku work?

First, we need to talk about how app use system APIs. For example, if the app wants to get installed apps, we all know we should use `PackageManager#getInstalledPackages()`. This is actually an interprocess communication (IPC) process of the app process and system server process, just the Android framework did the inner works for us.

Android uses `binder` to do this type of IPC. `Binder` allows the server-side to learn the uid and pid of the client-side, so that the system server can check if the app has the permission to do the operation.

Usually, if there is a "manager" (e.g., `PackageManager`) for apps to use, there should be a "service" (e.g., `PackageManagerService`) in the system server process. We can simply think if the app holds the `binder` of the "service", it can communicate with the "service". The app process will receive binders of system services on start.

Shizuku guides users to run a process, Shizuku server, with root or ADB first. When the app starts, the `binder` to Shizuku server will also be sent to the app.

The most important feature Shizuku provides is something like be a middle man to receive requests from the app, sent them to the system server, and send back the results. You can see the `transactRemote` method in `rikka.shizuku.server.ShizukuService` class, and `moe.shizuku.api.ShizukuBinderWrapper` class for the detail.

So, we reached our goal, to use system APIs with higher permission. And to the app, it is almost identical to the use of system APIs directly.

## Developer guide

### API & sample

https://github.com/RikkaApps/Shizuku-API

### Migrating from pre-v11

> Existing applications still works, of course.

https://github.com/RikkaApps/Shizuku-API#migration-guide-for-existing-applications-use-shizuku-pre-v11

### Attention

1. ADB permissions are limited

   ADB has limited permissions and different on various system versions. You can see permissions granted to ADB [here](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/Shell/AndroidManifest.xml).

   Before calling the API, you can use `ShizukuService#getUid` to check if Shizuku is running user ADB, or use `ShizukuService#checkPermission` to check if the server has sufficient permissions.

2. Hidden API limitation from Android 9

   As of Android 9, the usage of the hidden APIs is limited for normal apps. Please use other methods (such as <https://github.com/LSPosed/AndroidHiddenApiBypass>).

3. Android 8.0 & ADB

   At present, the way Shizuku service gets the app process is to combine `IActivityManager#registerProcessObserver` and `IActivityManager#registerUidObserver` (26+) to ensure that the app process will be sent when the app starts. However, on API 26, ADB lacks permissions to use `registerUidObserver`, so if you need to use Shizuku in a process that might not be started by an Activity, it is recommended to trigger the send binder by starting a transparent activity.

4. Direct use of `transactRemote` requires attention

   * The API may be different under different Android versions, please be sure to check it carefully. Also, the `android.app.IActivityManager` has the aidl form in API 26 and later, and `android.app.IActivityManager$Stub` exists only on API 26.

   * `SystemServiceHelper.getTransactionCode` may not get the correct transaction code, such as `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages` does not exist on API 25 and there is `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages_47` (this situation has been dealt with, but it is not excluded that there may be other circumstances). This problem is not encountered with the `ShizukuBinderWrapper` method.

## Developing Shizuku itself

### Build

- Clone with `git clone --recurse-submodules`
- Run gradle task `:manager:assembleDebug` or `:manager:assembleRelease`

The `:manager:assembleDebug` task generates a debuggable server. You can attach a debugger to `shizuku_server` to debug the server. Be aware that, in Android Studio, "Run/Debug configurations" - "Always install with package manager" should be checked, so that the server will use the latest code.

## License

All code files in this project are licensed under Apache 2.0

Under Apache 2.0 section 6, specifically:

* You are **FORBIDDEN** to use `manager/src/main/res/mipmap*/ic_launcher*.png` image files, unless for displaying Shizuku itself.

* You are **FORBIDDEN** to use `Shizuku` as app name or use `moe.shizuku.privileged.api` as application id or declare `moe.shizuku.manager.permission.*` permission. 


*adriand16f1982e9eeaa6235926c36283aaa0aec55034cadb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh


# Introducción a GitHub Enterprise Server

Introducción a la configuración y administración de GitHub.com.

Esta guía le guiará en la puesta en marcha, la configuración y la administración de tu instancia de GitHub Enterprise Server como administrador empresarial.

GitHub proporciona dos maneras de implementar GitHub Enterprise.

* **GitHub Enterprise Cloud**
* **GitHub Enterprise Server**

GitHub hospeda GitHub Enterprise Cloud. Puede implementar y hospedar GitHub Enterprise Server en su propio centro de datos o en un proveedor de nube compatible.

Para obtener más información sobre GitHub Enterprise Server, consulta [Acerca de GitHub Enterprise Server](/es/enterprise-server@3.21/admin/overview/about-github-enterprise-server).

## Parte 1: Instalación GitHub Enterprise Server

Para empezar, necesitará crear su cuenta empresarial, instalar la instancia, usar Consola de administración para la configuración inicial, configurar su instancia y administrar la facturación.

### 1. Creación de una cuenta empresarial

Antes de instalar GitHub Enterprise Server, puede crear una cuenta empresarial en GitHub.com poniéndose en contacto con el equipo de ventas de GitHub. Una cuenta de empresa en GitHub.com es útil para la facturación y para las características compartidas con GitHub.com a través de GitHub Connect. Para más información, consulta [Cuenta de empresa](/es/enterprise-server@3.21/admin/managing-your-enterprise-account/about-enterprise-accounts).

### 2. Instalar GitHub Enterprise Server

Para empezar, necesitarás instalar el dispositivo en una plataforma de virtualización que elijas. Para más información, consulta [Configuración de una instancia de GitHub Enterprise Server](/es/enterprise-server@3.21/admin/installation/setting-up-a-github-enterprise-server-instance).

### 3. Uso de Consola de administración

Usará Consola de administración para realizar el proceso de configuración inicial al iniciar tu instancia de GitHub Enterprise Server por primera vez. También puede usar Consola de administración para administrar la configuración de instancia, como la licencia, el dominio, la autenticación y TLS. Para más información, consulta [Administrar la instancia desde la interfaz del usuario web](/es/enterprise-server@3.21/admin/administering-your-instance/administering-your-instance-from-the-web-ui).

### 4. Configuración tu instancia de GitHub Enterprise Server

Además de Consola de administración, puede usar el panel de administración del sitio y el shell administrativo (SSH) para administrar tu instancia de GitHub Enterprise Server. Por ejemplo, puedes configurar las aplicaciones y límites de tasa, ver reportes y utilizar utilidades de línea de comandos. Para más información, consulta [Configuración de GitHub Enterprise](/es/enterprise-server@3.21/admin/configuration).

Puede usar la configuración de red predeterminada que usa GitHub Enterprise Server a través del protocolo de configuración dinámica de host (DHCP) o también puede configurar las opciones de red mediante la consola de la máquina virtual. También puedes configurar un servidor proxy o reglas de firewall. Para más información, consulta [Definición de la configuración de red](/es/enterprise-server@3.21/admin/configuration/configuring-network-settings).

### 5. Configuración de la alta disponibilidad

Puede configurar tu instancia de GitHub Enterprise Server para alta disponibilidad a fin de minimizar el impacto de los fallos de hardware y las interrupciones de red. Para más información, consulta [Configuración de alta disponibilidad](/es/enterprise-server@3.21/admin/monitoring-and-managing-your-instance/configuring-high-availability).

### 6. Configuración de una instancia de ensayo

Puede configurar una instancia de almacenamiento provisional para probar las modificaciones, planear la recuperación ante desastres y probar las actualizaciones antes de aplicarlas a tu instancia de GitHub Enterprise Server. Para más información, consulta [Configurar una instancia de preparación](/es/enterprise-server@3.21/admin/installation/setting-up-a-github-enterprise-server-instance/setting-up-a-staging-instance).

### 7. Configuración de copias de seguridad y de la recuperación ante desastres

Para proteger los datos de producción, puede configurar copias de seguridad automatizadas de tu instancia de GitHub Enterprise Server con GitHub Enterprise Server Backup Service. Para más información, consulta [Acerca del servicio de copia de seguridad de GitHub Enterprise Server](/es/enterprise-server@3.21/admin/backing-up-and-restoring-your-instance/about-the-backup-service-for-github-enterprise-server).

### 8. Administración de la facturación de la empresa

La facturación de todas las organizaciones e instancias GitHub Enterprise Server conectadas a su cuenta de empresa se consolida en un único cargo en la factura por todos sus servicios de pago de GitHub.com. Los propietarios y gerentes de facturación de las empresas pueden acceder y administrar los ajustes de facturación de las cuentas empresariales. Para más información, consulta [Facturación de GitHub Enterprise](/es/enterprise-server@3.21/billing/managing-your-billing/about-billing-for-your-enterprise).

## Parte 2: Organizar y administrar tu equipo

Como propietario empresarial o administrador, puedes administrar los ajustes a nivel de usuario, repositorio, equipo y organización. Puedes administrar a los miembros de tu empresa, crear y administrar organizaciones, configurar políticas para la administración de repositorios y crear y administrar equipos.

### 1. Administrar miembros de tu instancia de GitHub Enterprise Server

Puedes administrar la configuración y la actividad de auditoría para los miembros de tu instancia de GitHub Enterprise Server. Puedes promover a un miembro de la empresa para que sea un adminsitrador de sitio, administrar usuarios inactivos, ver la bitácora de auditoría para la actividad de usuario y personalizar los mensajes que verán los miembros empresariales. Para más información, consulta [Administrar los usuarios en tu empresa](/es/enterprise-server@3.21/admin/user-management/managing-users-in-your-enterprise).

### 2. Creación de organizaciones

Puedes crear nuevas organizaciones en tu instancia de GitHub Enterprise Server para reflejar la estructura de tu grupo o empresa. Para más información, consulta [Crear una organización nueva desde cero](/es/enterprise-server@3.21/organizations/collaborating-with-groups-in-organizations/creating-a-new-organization-from-scratch).

### 3. Adición de miembros a las organizaciones

Puedes agregar miembros a las organizaciones en tu instancia de GitHub Enterprise Server mientras seas propietario de una de las organizaciones que quieres administrar. También puedes configurar la visibilidad de la membrecía de la organización. Para más información, consulta [Agregar personas a tu organización](/es/enterprise-server@3.21/organizations/managing-membership-in-your-organization/adding-people-to-your-organization) y [Configurar visibilidad para los miembros de la organización](/es/enterprise-server@3.21/admin/user-management/managing-organizations-in-your-enterprise/configuring-visibility-for-organization-membership).

### 4. Creación de equipos

Los equipos son grupos de miembros de organizaciones a los que se pueden otorgar permisos a repositorios específicos como un grupo. Puedes crear equipos individuales o niveles múltiples de equipos anidados en cada una de tus organizaciones. Para más información, consulta [Creación de un equipo de organización](/es/enterprise-server@3.21/organizations/organizing-members-into-teams/creating-a-team) y [Agregar miembros de la organización a un equipo](/es/enterprise-server@3.21/organizations/organizing-members-into-teams/adding-organization-members-to-a-team).

### 5. Configuración de niveles de permiso de organización y repositorio

Te recomendamos proporcionar una cantidad limitada de miembros en cada organización y rol de propietario de organización, lo cual proporciona acceso administrativo completo para ellas. Para más información, consulta [Roles en una organización](/es/enterprise-server@3.21/organizations/managing-peoples-access-to-your-organization-with-roles/roles-in-an-organization).

En el caso de las organizaciones en donde tienes permisos administrativos, también puedes personalizar el acceso a cada repositorio con niveles de permiso granulares. Para más información, consulta [Roles de repositorio para una organización](/es/enterprise-server@3.21/organizations/managing-user-access-to-your-organizations-repositories/managing-repository-roles/repository-roles-for-an-organization).

### 6. Aplicación de directivas de administración de repositorios

Como propietario de empresa, puedes configurar políticas de administración de repositorios para todas las organizaciones de tu instancia de GitHub Enterprise Server o permitir que las políticas se configuren por separado en cada organización. Para más información, consulta [Implantar políticas de gestión de repositorios en su empresa](/es/enterprise-server@3.21/admin/policies/enforcing-policies-for-your-enterprise/enforcing-repository-management-policies-in-your-enterprise).

### 7. Creación de un archivo README para la empresa

Para ayudar a las personas a comprender lo que sucede en su empresa, debe crear un archivo LÉAME. Por ejemplo, puede usar un archivo LÉAME para ayudar a los miembros a obtener información sobre diferentes organizaciones de la empresa, compartir vínculos a recursos importantes o comunicar información sobre la configuración y las directivas de su empresa. Para obtener más información, consulte [Creación de un archivo LÉAME para una empresa](/es/enterprise-server@3.21/admin/managing-your-enterprise-account/creating-a-readme-for-an-enterprise).

## Parte 3: Compilar de forma segura

Para aumentar la seguridad de tu instancia de GitHub Enterprise Server, puede configurar la autenticación para los miembros de la empresa, usar herramientas y los registros de auditoría para mantener el cumplimiento normativo, configurar funciones de seguridad y análisis para sus organizaciones y, opcionalmente, habilitar las funciones de GitHub Advanced Security.

### 1. Autenticación de los miembros de la empresa

Puede usar  GitHub Enterprise Serverel método de autenticación integrado, o puede elegir entre un proveedor de autenticación externo, como CAS, LDAP o SAML, para integrar las cuentas existentes y administrar de forma centralizada el acceso de los usuarios a tu instancia de GitHub Enterprise Server. Para más información, consulta [Aspectos básicos de la administración de identidades y acceso](/es/enterprise-server@3.21/admin/identity-and-access-management/understanding-iam-for-enterprises/about-identity-and-access-management).

También puedes requerir la autenticación bifactorial para cada una de tus organizaciones. Para más información, consulta [Solicitar autenticación de dos factores para una organización](/es/enterprise-server@3.21/admin/managing-accounts-and-repositories/managing-organizations-in-your-enterprise/requiring-two-factor-authentication-for-an-organization).

### 2. Mantenimiento del cumplimiento

Puedes implementar las verificaciones de estado requeridas y confirmar las verificaciones para hacer cumplir los estándares de cumplimiento de tu organización y automatizar los flujos de trabajo de cumplimiento. También puedes utilizar la bitácora de auditoría de tu organización para revisar las acciones que realiza tu equipo. Para más información, consulta [Requerir políticas para los ganchos de pre-recepción](/es/enterprise-server@3.21/admin/policies/enforcing-policy-with-pre-receive-hooks) y [Registro de auditoría de una empresa](/es/enterprise-server@3.21/admin/monitoring-activity-in-your-enterprise/reviewing-audit-logs-for-your-enterprise/about-the-audit-log-for-your-enterprise).

### 3. Configuración de las características de seguridad de las organizaciones

Para mantener protegidas las organizaciones dentro de tu instancia de GitHub Enterprise Server, puede usar una variedad de funciones de GitHubseguridad, incluidas las políticas de seguridad, los gráficos de dependencias, el escaneo de secretos, y las actualizaciones de seguridad y de versión de Dependabot. Para obtener más información, consulte [Configuración de características de seguridad en su organización](/es/enterprise-server@3.21/code-security/securing-your-organization).

### 4. Habilitar las funciones GitHub Advanced Security

Puede actualizar la GitHub Enterprise Server licencia para incluir GitHub Code Security o GitHub Secret Protection. La actualización proporcionará características adicionales que ayudan a los usuarios a encontrar y corregir problemas de seguridad en su código, como el análisis de secretos y de código. Para más información, consulta [Habilitar productos GitHub Advanced Security para su empresa](/es/enterprise-server@3.21/admin/code-security/managing-github-advanced-security-for-your-enterprise/enabling-github-advanced-security-for-your-enterprise).

## Parte 4: Personalizar y automatizar el trabajo de su empresa en GitHub

Puede personalizar y automatizar el trabajo en organizaciones de su empresa con GitHub y OAuth apps, GitHub Enterprise Server API, GitHub Actions, GitHub Packages y GitHub Pages.

### 1. Edificio GitHub Apps y OAuth apps

Puede crear integraciones con la GitHub Enterprise Server API, como GitHub Apps o OAuth apps, para usarlas en organizaciones de su empresa para complementar y ampliar los flujos de trabajo. Para más información, consulta [Acerca de la creación de aplicaciones de GitHub](/es/enterprise-server@3.21/apps/creating-github-apps/about-creating-github-apps/about-creating-github-apps).

### 2. Uso de la GitHub Enterprise Server API

Hay dos versiones de la API de GitHub: la API REST y la API de GraphQL. Puedes usar las API GitHub para automatizar tareas comunes, [hacer copias de seguridad de los datos](/es/enterprise-server@3.21/repositories/archiving-a-github-repository/backing-up-a-repository) o crear integraciones que amplíen GitHub. Para más información, consulta [Comparación de la API REST de GitHub y GraphQL API](/es/enterprise-server@3.21/rest/overview/about-githubs-apis).

### 3. Edificio GitHub Actions

Con GitHub Actions, puedes automatizar y personalizar el flujo de desarrollo de tu empresa en GitHub. Puedes crear tus propias acciones y usar y personalizar acciones compartidas por la comunidad de GitHub. Para más información, consulta [Escritura de flujos de trabajo](/es/enterprise-server@3.21/actions/learn-github-actions).

Para obtener más información sobre cómo habilitar y configurar GitHub Actions en  GitHub Enterprise Server, vea [Introducción a GitHub Actions para GitHub Enterprise Server](/es/enterprise-server@3.21/admin/github-actions/getting-started-with-github-actions-for-your-enterprise/getting-started-with-github-actions-for-github-enterprise-server).

### 4. Publicación y administración GitHub Packages

GitHub Packages es un servicio de alojamiento de paquete de software que te permite alojar tus paquetes de software de forma privada o pública y usar paquetes como dependencias en tus proyectos. Para más información, consulta [Introducción a los paquetes de GitHub](/es/enterprise-server@3.21/packages/learn-github-packages/introduction-to-github-packages).

Para obtener más información sobre cómo habilitar y configurar GitHub Packages para tu instancia de GitHub Enterprise Server, vea [Introducción a los paquetes de GitHub para su empresa](/es/enterprise-server@3.21/admin/packages/getting-started-with-github-packages-for-your-enterprise).

### 5. Uso de GitHub Pages

GitHub Pages es un servicio de hospedaje de sitios estáticos que toma archivos de HTML, CSS y JavaScript directamente desde un repositorio y publica un sitio web. Puedes habilitar o inhabilitar las GitHub Pages para tus miembros empresariales a nivel de organización. Para más información, consulta [Configuración de GitHub Pages para su empresa](/es/enterprise-server@3.21/admin/configuration/configuring-your-enterprise/configuring-github-pages-for-your-enterprise) y [¿Qué es GitHub Pages?](/es/enterprise-server@3.21/pages/getting-started-with-github-pages/about-github-pages).

## Parte 5: Conexión con otros GitHub recursos

Puede usar GitHub Connect para compartir recursos.

Si usted es propietario de una instancia de GitHub Enterprise Server y de una cuenta de organización o empresa de GitHub Enterprise Cloud, puede habilitar GitHub Connect.
GitHub Connect permite compartir flujos de trabajo y características específicos entre tu instancia de GitHub Enterprise Server y GitHub Enterprise Cloud, como la búsqueda unificada y las contribuciones. Para más información, consulta [Habilitación de GitHub Connect para GitHub.com](/es/enterprise-server@3.21/admin/configuration/configuring-github-connect/managing-github-connect).

## Parte 6: Uso de los recursos de aprendizaje y asistencia de GitHub

Los miembros de la empresa pueden obtener más información sobre Git y GitHub con nuestros recursos de aprendizaje, y puede obtener el soporte técnico que necesita al configurar y administrar tu instancia de GitHub Enterprise Server con GitHub el soporte técnico empresarial.

### 1. Leer sobre GitHub Enterprise Server en GitHub Docs

Puede leer documentación que refleje las características disponibles con GitHub Enterprise Server. Para más información, consulta [Acerca de las versiones de GitHub Docs](/es/enterprise-server@3.21/get-started/using-github-docs/about-versions-of-github-docs).

Para saber cómo la empresa puede usar GitHub de manera más eficaz, consulta [Procedimientos recomendados para organizar el trabajo en su empresa](/es/enterprise-server@3.21/admin/overview/best-practices-for-enterprises).

### 2. Aprendizaje con GitHub Skills

Los miembros de la empresa pueden aprender aptitudes nuevas si completan proyectos divertidos y realistas en su propio repositorio de GitHub con [GitHub Skills](https://skills.github.com/). Cada curso es una lección práctica que ha creado la comunidad de GitHub y lo imparte un simpático bot.

Para más información, consulta [Recursos de aprendizaje de Git y GitHub](/es/enterprise-server@3.21/get-started/start-your-journey/git-and-github-learning-resources).

### 3. Trabajar con compatibilidad empresarial de GitHub

GitHub Enterprise incluye el acceso a Soporte técnico para GitHub Enterprise. Soporte técnico para GitHub Enterprise puede ayudarte a solucionar problemas. También puedes elegir registrarte para las características adicionales del Soporte Premium de GitHub. Para obtener más información, consulta [Acerca de la compatibilidad con GitHub](/es/enterprise-server@3.21/support/learning-about-github-support/about-github-support).[![Node.js CI](https://github.com/neftali89016-collab/FCM-for-Mojo-Server/actions/workflows/node.js.yml/badge.svg)](https://github.com/neftali89016-collab/FCM-for-Mojo-Server/actions/workflows/node.js.yml)