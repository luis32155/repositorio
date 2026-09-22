from pathlib import Path
base = Path(__file__).with_name('build_manual.py').read_text(encoding='utf-8')
exec(base.split("doc.add_paragraph('Manual de onboarding de MongoDB Atlas', 'Title')")[0])
doc.add_paragraph('Propuesta de onboarding MongoDB Atlas para BHXW', 'Title')
p('Nombres propuestos y borrador de ticket para completar')
p('Esta propuesta incorpora los datos confirmados de la aplicación y reúne la información necesaria para solicitar el onboarding. Los campos entre corchetes siguen pendientes. Los nombres de recursos deben validarse con los equipos responsables antes de su creación.')
h('1 Datos confirmados')
table(['Dato','Valor'], [('EPM','BHXW'),('Transit Code para Nube','44834'),('Colección utilizada por el servicio','transaction_projection')], [2.7,3.9])
p('CIAD code y OP Code siguen pendientes. No se asume que sean iguales al EPM o al Transit Code. La disponibilidad de estos códigos no constituye evidencia de financiamiento aprobado.')
h('2 Nombres propuestos')
p('Se propone tapptrx como nombre corto por TAPP Transacciones. Solo el formato del usuario de aplicación proviene del manual de onboarding; los demás nombres son propuestas sujetas a las convenciones internas.')
table(['Recurso','Propuesta'],[
('Nombre descriptivo','TAPP Transaction Read Model'),('Nombre corto','tapptrx'),('Proyecto MongoDB Atlas','bhxw-tapptrx-<ambiente>'),('Clúster MongoDB','bhxw-tapptrx-<ambiente>-01'),('Base de datos','tapp_transaction_read_model'),('Cuenta de servicio KMS','bhxw-tapptrx-<ambiente>-kms'),('Keyring','bhxw-tapptrx-<ambiente>-kr'),('Clave de cifrado','bhxw-tapptrx-<ambiente>-key')],[2.2,4.4])
p('Confirmar el nombre oficial asociado a BHXW, la separación de proyectos por ambiente y el nombre de base de datos en la configuración de despliegue. Sustituir <ambiente> por el ambiente aprobado, por ejemplo ist o uat.')

page(); h('3 Usuarios y recursos por confirmar')
table(['Ambiente','Usuario propuesto'],[('IST','app_bhxw_tapptrx_i_1'),('UAT','app_bhxw_tapptrx_u_1'),('NFT','app_bhxw_tapptrx_n_1'),('PROD','app_bhxw_tapptrx_p_1')],[1.5,5.1])
p('La ruta de Vault, el grupo AD y las etiquetas de red deben corresponder a los recursos asignados por la plataforma. Solicitarlos a los equipos responsables; no crear valores arbitrarios para completar el ticket.')
h('4 Información pendiente para completar la solicitud')
table(['Dato','Información requerida'],[
('Ambiente inicial','[IST / UAT / NFT / PROD]'),('Identificación financiera','[CIAD code] / [OP Code] / [Estado del financiamiento]'),('Responsable','[Nombre, equipo y contacto]'),('Ubicación de la aplicación','[Región y clúster de Scotia Atlas Platform]'),('Seguridad','[Security Advisor, clasificación de datos y referencia TRA]'),('Aprobaciones y referencias','[ARB, onboarding de plataforma y DOU]'),('Dimensionamiento','[Documentos/transacciones por día, tamaño promedio, retención y carga máxima esperada]'),('Fecha requerida','[Fecha en la que se necesita el ambiente]')],[2.0,4.6])
p('Las IP privadas de PSC y la cadena de conexión se obtienen durante la configuración. No es necesario conocerlas para redactar la solicitud inicial.')
h('5 Revisión antes del envío')
bullets(['Seleccionar un ambiente y su usuario correspondiente.','Confirmar nombre oficial de la aplicación y validar propuestas de nombres.','Completar CIAD code, OP Code, responsable, región y fecha.','Registrar el estado real de las aprobaciones; no marcar como aprobado lo que sigue pendiente.','Adjuntar diseño, dimensionamiento, presupuesto, aprobaciones y DOU disponibles.','Indicar expresamente cualquier pendiente y solicitar orientación para resolverlo.'],True)

page(); h('6 Borrador de ticket a DBA')
p('Asunto: [BHXW] Solicitud de onboarding MongoDB Atlas para TAPP Transaction Read Model — [AMBIENTE]')
p('Grupo destinatario: GTS - DBO - MONGODB')
p('Hola, equipo:')
p('Solicitamos iniciar el onboarding de MongoDB Atlas para el servicio de proyección de transacciones de TAPP, asociado al EPM BHXW, para el ambiente [IST/UAT/NFT/PROD].')
p('La aplicación consume eventos de transacciones desde Kafka y mantiene una vista consolidada en MongoDB. La colección utilizada por el servicio es transaction_projection.')
sub('Datos de la aplicación')
bullets(['EPM: BHXW.','Transit Code para Nube: 44834.','Nombre descriptivo propuesto: TAPP Transaction Read Model.','Nombre corto propuesto: tapptrx.','CIAD code: [Pendiente de confirmar].','OP Code: [Pendiente de confirmar].','Ambiente: [Completar].','Región y clúster de Scotia Atlas Platform: [Completar].','Responsable técnico y contacto: [Completar].','Fecha requerida: [Completar].'])
sub('Nombres propuestos para validación')
bullets(['Proyecto MongoDB Atlas: bhxw-tapptrx-<ambiente>.','Clúster: bhxw-tapptrx-<ambiente>-01.','Base de datos: tapp_transaction_read_model.','Colección utilizada por la aplicación: transaction_projection.','Usuario: [Seleccionar según ambiente: app_bhxw_tapptrx_i_1 / app_bhxw_tapptrx_u_1 / app_bhxw_tapptrx_n_1 / app_bhxw_tapptrx_p_1].'])
p('Solicitamos confirmar estos nombres conforme a las convenciones internas antes de crear los recursos.')

page(); h('Continuación del borrador de ticket')
sub('Alcance solicitado')
for s in ['Crear y configurar el proyecto y clúster MongoDB Atlas conforme al diseño y dimensionamiento acordados.','Aplicar las etiquetas de asignación de costos correspondientes a la aplicación.','Configurar la integración del lado de MongoDB con GCP KMS y Private Service Connect.','Proporcionar los insumos necesarios para los tickets dependientes de COPS, GNE y firewall.','Coordinar la creación del usuario de aplicación y su integración con HashiCorp Vault según el ambiente.','Apoyar las integraciones de Dynatrace, ESLM y GEMS cuando correspondan.']:
    doc.add_paragraph(s,style='List Number')
p('La aplicación requiere escribir y actualizar la proyección de transacciones. Solicitamos revisar con DBA los permisos mínimos requeridos, incluyendo la gestión de índices.')
sub('Estado de requisitos')
bullets(['GTEP ARB: [Estado y referencia].','Diseño y dimensionamiento: [Adjunto/Pendiente].','Presupuesto y financiamiento: [Estado y evidencia].','TRA: [Estado y referencia].','Security Advisor: [Nombre].','Clasificación de datos: [Completar].','Onboarding Scotia Atlas Platform: [Estado y referencia].','DOU y aprobaciones de seguridad: [Estado y referencias].'])
sub('Coordinación')
p('Nuestro equipo gestionará las solicitudes de los equipos involucrados, el FPR y la validación de conectividad desde la aplicación.')
p('Solicitamos confirmar la información adicional requerida, las dependencias para iniciar y el plazo estimado de atención. Si el dimensionamiento está pendiente, solicitamos indicar los datos necesarios para completarlo antes del despliegue.')
sub('Adjuntos')
p('[Diseño de la solución]\n[Dimensionamiento y presupuesto]\n[Aprobaciones]\n[DOU]\n[Referencias de solicitudes relacionadas]')
p('Gracias.\n[Nombre / equipo / contacto]')
doc.core_properties.title='Propuesta de onboarding MongoDB Atlas para BHXW'
doc.core_properties.subject='Nombres propuestos, ticket y datos pendientes'
path=OUT/'Propuesta_ticket_MongoDB_BHXW.docx'
doc.save(path)
from zipfile import ZipFile
with ZipFile(path) as z: assert z.testzip() is None
print(path)
