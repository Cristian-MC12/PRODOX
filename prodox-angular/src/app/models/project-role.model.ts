// Autor: Cristian Santiago Martinez Cordoba — PRODOX
// Rol POR PROYECTO (V39 — Product Owner). Centraliza los tres valores válidos
// de ProjectMember.rol y su etiqueta visible, para que ningún componente
// vuelva a asumir el patrón binario "si no es Scrum Master, es Scrum Member"
// (ya existe un tercer rol: Product Owner).
export const ROL_SCRUM_MASTER = 'scrum_master';
export const ROL_PRODUCT_OWNER = 'product_owner';
export const ROL_SCRUM_MEMBER = 'scrum_member';

export type RolProyecto = typeof ROL_SCRUM_MASTER | typeof ROL_PRODUCT_OWNER | typeof ROL_SCRUM_MEMBER;

const ETIQUETAS: Record<string, string> = {
  [ROL_SCRUM_MASTER]: 'Scrum Master',
  [ROL_PRODUCT_OWNER]: 'Product Owner',
  [ROL_SCRUM_MEMBER]: 'Scrum Member',
};

export function etiquetaRol(rol: string | null | undefined): string {
  return (rol && ETIQUETAS[rol]) || 'Scrum Member';
}
