export interface CopilotConfig {
  id?: string;
  userId?: string;
  tool: 'jira' | 'github';
  url: string;
  apiKey: string;
  frequency: 'hourly' | 'every_6h' | 'daily' | 'weekly';
  active: boolean;
  lastSyncAt?: string | null;
}
