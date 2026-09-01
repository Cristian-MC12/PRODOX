export interface Factor {
  id: string;
  name: string;
  description: string;
  category: string;
}

export interface SprintSelection {
  id: string;
  factorId: string;
  sprintName: string;
}

export interface SelectFactorRequest {
  factorId: string;
  sprintName: string;
}
