# Issues P0 — pegar en GitHub Project

Crear en cada repo (Add to project → Servicio BFF o MS-Catalog).

## andesstay-ms-bff

1. `feat: skeleton BFF con /api/health y /api/me`
2. `feat: validar issuer, audience, firma y expiracion del JWT`
3. `feat: autorizacion por rol en endpoints del BFF (401/403)`
4. `feat: proxy/orquestacion del BFF hacia catalog y reservations`
5. `feat: contrato BFF estable y errores uniformes`
6. `docs: OpenAPI del BFF`
7. `feat: BFF en Docker con validacion de audience exacta`
8. `docs: collection de pruebas API (Postman/Bruno)`

## andesstay-ms-catalog

9. `feat: CRUD /api/catalog/units`
10. `docs: OpenAPI del microservicio catalogo`

Plantilla:

```markdown
## Criterios de aceptación
- [ ] ...

## Servicio
BFF | MS-Catalog

## Notas
- Rama: feature/ep1-p0
- PR: Closes #N
```
