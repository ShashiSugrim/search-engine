const express = require('express');
const { exec } = require('child_process');
const fs = require('fs').promises;
const path = require('path');

const app = express();
const port = 3000;

// Middleware to parse JSON bodies
app.use(express.json());

// Replace hardcoded file_path with parameter from command-line arguments
const file_path = process.argv[2] ;

// Search endpoint
app.post('/search', async (req, res) => {
    try {
        const { query } = req.body;
        
        if (!query) {
            return res.status(400).json({ error: 'Search query is required' });
        }

        // Execute Java command
        const command = `java -jar IsolatedTask9CS335-all.jar -FILE_DIR=${file_path} -SEARCH=QUERY "${query}" -GUI=false -output="output.txt"`;
        
        exec(command, async (error, stdout, stderr) => {
            if (error) {
                console.error(`Error: ${error}`);
                return res.status(500).json({ error: 'Error executing search' });
            }

            try {
                // Read the output file
                const outputPath = path.join(__dirname, 'output.txt');
                const data = await fs.readFile(outputPath, 'utf8');
                const jsonData = JSON.parse(data);
                
                res.json(jsonData);
            } catch (readError) {
                console.error(`Error reading output file: ${readError}`);
                res.status(500).json({ error: 'Error reading search results' });
            }
        });
    } catch (err) {
        console.error(`Server error: ${err}`);
        res.status(500).json({ error: 'Internal server error' });
    }
});

// Start server
app.listen(port, () => {
    console.log(`Server running on port ${port}`);
});