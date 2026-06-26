// Autor: Cristian Santiago Martinez Cordoba — MPDIA

export interface RegistroValorDto {
  id:             string;
  variableId:     string;
  variableNombre: string;
  sprintId:       string;
  userId:         string;
  valorNum:       number | null;
  valorTexto:     string | null;
  valorBool:      boolean | null;
  observacion:    string | null;
  registradoAt:   string;
}

export interface RegistrarValorRequest {
  variableId:   string;
  sprintId:     string;
  valorNum?:    number;
  valorTexto?:  string;
  valorBool?:   boolean;
  observacion?: string;
}
