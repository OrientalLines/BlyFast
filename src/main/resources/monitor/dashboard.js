// Theme toggle
const themeToggle = document.getElementById('theme-toggle');
const prefersDarkScheme = window.matchMedia('(prefers-color-scheme: dark)');

// Check for saved theme or use system preference
const currentTheme = localStorage.getItem('theme') || 
    (prefersDarkScheme.matches ? 'dark' : 'light');

if (currentTheme === 'dark') {
    document.body.setAttribute('data-theme', 'dark');
    themeToggle.innerHTML = '<i class="fas fa-sun"></i> Light Mode';
}

themeToggle.addEventListener('click', () => {
    const currentTheme = document.body.getAttribute('data-theme') || 'light';
    const newTheme = currentTheme === 'light' ? 'dark' : 'light';
    
    document.body.setAttribute('data-theme', newTheme);
    localStorage.setItem('theme', newTheme);
    
    themeToggle.innerHTML = newTheme === 'dark' 
        ? '<i class="fas fa-sun"></i> Light Mode' 
        : '<i class="fas fa-moon"></i> Dark Mode';
        
    // Update chart themes
    updateChartsTheme();
});

// Format date and time
function formatDate(timestamp) {
    return new Date(timestamp).toLocaleString();
}

// Format bytes to human-readable format
function formatBytes(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// Format time in milliseconds to human-readable format
function formatTime(ms) {
    if (ms < 1000) return ms + ' ms';
    const seconds = ms / 1000;
    if (seconds < 60) return seconds.toFixed(2) + ' sec';
    const minutes = seconds / 60;
    if (minutes < 60) return minutes.toFixed(2) + ' min';
    const hours = minutes / 60;
    return hours.toFixed(2) + ' hrs';
}

// Initialize charts
const requestsChart = new Chart(
    document.getElementById('requests-chart'),
    {
        type: 'line',
        data: {
            labels: [],
            datasets: [
                {
                    label: 'Total Requests',
                    data: [],
                    borderColor: getComputedStyle(document.body).getPropertyValue('--primary'),
                    backgroundColor: 'rgba(67, 97, 238, 0.1)',
                    tension: 0.3,
                    fill: true
                },
                {
                    label: 'Errors',
                    data: [],
                    borderColor: getComputedStyle(document.body).getPropertyValue('--danger'),
                    backgroundColor: 'rgba(231, 76, 60, 0.1)',
                    tension: 0.3,
                    fill: true
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'top',
                    labels: {
                        font: {
                            family: "'Segoe UI', sans-serif",
                            size: 12
                        }
                    }
                }
            },
            scales: {
                x: {
                    grid: {
                        color: getComputedStyle(document.body).getPropertyValue('--chart-grid')
                    }
                },
                y: {
                    beginAtZero: true,
                    grid: {
                        color: getComputedStyle(document.body).getPropertyValue('--chart-grid')
                    }
                }
            }
        }
    }
);

const responseTimeChart = new Chart(
    document.getElementById('response-time-chart'),
    {
        type: 'line',
        data: {
            labels: [],
            datasets: [
                {
                    label: 'Avg Response Time (ms)',
                    data: [],
                    borderColor: getComputedStyle(document.body).getPropertyValue('--secondary'),
                    backgroundColor: 'rgba(114, 9, 183, 0.1)',
                    tension: 0.3,
                    fill: true
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'top'
                }
            },
            scales: {
                x: {
                    grid: {
                        color: getComputedStyle(document.body).getPropertyValue('--chart-grid')
                    }
                },
                y: {
                    beginAtZero: true,
                    grid: {
                        color: getComputedStyle(document.body).getPropertyValue('--chart-grid')
                    }
                }
            }
        }
    }
);

const memoryChart = new Chart(
    document.getElementById('memory-chart'),
    {
        type: 'line',
        data: {
            labels: [],
            datasets: [
                {
                    label: 'Heap Memory Used',
                    data: [],
                    borderColor: getComputedStyle(document.body).getPropertyValue('--accent'),
                    backgroundColor: 'rgba(76, 201, 240, 0.1)',
                    tension: 0.3,
                    fill: true
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'top'
                }
            },
            scales: {
                x: {
                    grid: {
                        color: getComputedStyle(document.body).getPropertyValue('--chart-grid')
                    }
                },
                y: {
                    beginAtZero: true,
                    grid: {
                        color: getComputedStyle(document.body).getPropertyValue('--chart-grid')
                    }
                }
            }
        }
    }
);

const endpointsChart = new Chart(
    document.getElementById('endpoints-chart'),
    {
        type: 'bar',
        data: {
            labels: [],
            datasets: [
                {
                    label: 'Requests per Endpoint',
                    data: [],
                    backgroundColor: getComputedStyle(document.body).getPropertyValue('--primary'),
                    borderRadius: 5
                },
                {
                    label: 'Errors per Endpoint',
                    data: [],
                    backgroundColor: getComputedStyle(document.body).getPropertyValue('--danger'),
                    borderRadius: 5
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'top'
                }
            },
            scales: {
                x: {
                    grid: {
                        color: getComputedStyle(document.body).getPropertyValue('--chart-grid')
                    }
                },
                y: {
                    beginAtZero: true,
                    grid: {
                        color: getComputedStyle(document.body).getPropertyValue('--chart-grid')
                    }
                }
            }
        }
    }
);

// Time series data
const timeSeriesData = {
    timestamps: [],
    requests: [],
    errors: [],
    responseTime: [],
    memory: []
};

// Function to update charts theme
function updateChartsTheme() {
    const gridColor = getComputedStyle(document.body).getPropertyValue('--chart-grid');
    const textColor = getComputedStyle(document.body).getPropertyValue('--text');
    
    [requestsChart, responseTimeChart, memoryChart, endpointsChart].forEach(chart => {
        chart.options.scales.x.grid.color = gridColor;
        chart.options.scales.y.grid.color = gridColor;
        chart.options.plugins.legend.labels.color = textColor;
        chart.update();
    });
}

// Fetch monitoring data and update the dashboard
function updateDashboard() {
    fetch('/monitor/stats')
        .then(response => response.json())
        .then(data => {
            // Add timestamp and data to time series
            const now = new Date();
            const timeLabel = now.getHours().toString().padStart(2, '0') + ':' + 
                             now.getMinutes().toString().padStart(2, '0') + ':' + 
                             now.getSeconds().toString().padStart(2, '0');
            
            // Keep only the last 10 points for the charts
            if (timeSeriesData.timestamps.length >= 10) {
                timeSeriesData.timestamps.shift();
                timeSeriesData.requests.shift();
                timeSeriesData.errors.shift();
                timeSeriesData.responseTime.shift();
                timeSeriesData.memory.shift();
            }
            
            timeSeriesData.timestamps.push(timeLabel);
            timeSeriesData.requests.push(data.requests.total);
            timeSeriesData.errors.push(data.requests.errors);
            timeSeriesData.responseTime.push(data.requests.avgResponseTime);
            timeSeriesData.memory.push(data.jvm.heapUsed);
            
            // Update Overview and Request Stats
            document.getElementById('total-requests').textContent = data.requests.total;
            document.getElementById('active-requests').textContent = data.requests.active;
            document.getElementById('error-count').textContent = data.requests.errors;
            document.getElementById('avg-response-time').textContent = 
                data.requests.avgResponseTime.toFixed(2) + ' ms';
            document.getElementById('uptime').textContent = formatTime(data.uptime);
            
            // Update Memory Stats
            document.getElementById('heap-used').textContent = formatBytes(data.jvm.heapUsed);
            document.getElementById('heap-max').textContent = formatBytes(data.jvm.heapMax);
            
            // Update memory meter
            const memoryPercentage = (data.jvm.heapUsed / data.jvm.heapMax) * 100;
            document.getElementById('memory-meter').style.width = memoryPercentage + '%';
            
            // Update JVM Info
            document.getElementById('jvm-name').textContent = data.jvm.jvmName;
            document.getElementById('jvm-version').textContent = data.jvm.jvmVersion;
            document.getElementById('jvm-vendor').textContent = data.jvm.jvmVendor;
            document.getElementById('thread-count').textContent = data.jvm.threadCount;
            document.getElementById('cpu-load').textContent = 
                data.jvm.cpuLoad !== -1 ? data.jvm.cpuLoad.toFixed(2) : 'N/A';
            document.getElementById('start-time').textContent = formatDate(data.startTime);
            
            // Update Path Metrics Table
            let pathTableContent = '';
            const pathData = data.paths;
            
            // Update endpoint chart data
            const paths = Object.keys(pathData);
            const requestsPerPath = paths.map(p => pathData[p].requests);
            const errorsPerPath = paths.map(p => pathData[p].errors);
            
            endpointsChart.data.labels = paths;
            endpointsChart.data.datasets[0].data = requestsPerPath;
            endpointsChart.data.datasets[1].data = errorsPerPath;
            endpointsChart.update();
            
            // Generate table content
            for (const [path, metrics] of Object.entries(pathData)) {
                const isSlowPath = metrics.avgResponseTime > 500;
                const status = metrics.errors > 0 
                    ? '<span class="badge badge-danger">Issues</span>' 
                    : isSlowPath 
                        ? '<span class="badge badge-warning">Slow</span>' 
                        : '<span class="badge badge-success">Healthy</span>';
                
                pathTableContent += `
                    <tr class="${isSlowPath ? 'slow-request' : ''}">
                        <td>${path}</td>
                        <td>${metrics.requests}</td>
                        <td class="error-count">${metrics.errors}</td>
                        <td>${metrics.avgResponseTime.toFixed(2)}</td>
                        <td>${metrics.minResponseTime === 9223372036854776000 ? '0' : metrics.minResponseTime}</td>
                        <td>${metrics.maxResponseTime}</td>
                        <td>${status}</td>
                    </tr>
                `;
            }
            
            document.getElementById('path-metrics').innerHTML = 
                pathTableContent || '<tr><td colspan="7" style="text-align: center;">No path metrics available yet</td></tr>';
            
            // Update time series charts
            requestsChart.data.labels = timeSeriesData.timestamps;
            requestsChart.data.datasets[0].data = timeSeriesData.requests;
            requestsChart.data.datasets[1].data = timeSeriesData.errors;
            requestsChart.update();
            
            responseTimeChart.data.labels = timeSeriesData.timestamps;
            responseTimeChart.data.datasets[0].data = timeSeriesData.responseTime;
            responseTimeChart.update();
            
            memoryChart.data.labels = timeSeriesData.timestamps;
            memoryChart.data.datasets[0].data = timeSeriesData.memory;
            memoryChart.update();
        })
        .catch(error => {
            console.error('Error fetching monitoring data:', error);
        });
}

// Initial update
updateDashboard();
updateChartsTheme();

// Refresh interval management
let refreshInterval = localStorage.getItem('refreshInterval') || 5000;
let refreshIntervalId;

// Set the selected option in the dropdown based on saved preference
document.getElementById('refresh-interval').value = refreshInterval;

// Update the refresh interval display
function updateRefreshDisplay(interval) {
    const intervalMs = parseInt(interval);
    let display;
    
    if (intervalMs < 1000) {
        display = intervalMs + ' ms';
    } else if (intervalMs === 1000) {
        display = '1 second';
    } else if (intervalMs < 60000) {
        display = (intervalMs / 1000) + ' seconds';
    } else if (intervalMs === 60000) {
        display = '1 minute';
    } else {
        display = (intervalMs / 60000) + ' minutes';
    }
    
    document.getElementById('refresh-display').textContent = display;
}

// Initialize refresh interval display
updateRefreshDisplay(refreshInterval);

// Function to start the refresh interval
function startRefreshInterval() {
    // Clear any existing interval
    if (refreshIntervalId) {
        clearInterval(refreshIntervalId);
    }
    
    // Start new interval
    refreshIntervalId = setInterval(updateDashboard, parseInt(refreshInterval));
}

// Listen for changes to the refresh interval selector
document.getElementById('refresh-interval').addEventListener('change', function(e) {
    refreshInterval = e.target.value;
    localStorage.setItem('refreshInterval', refreshInterval);
    updateRefreshDisplay(refreshInterval);
    startRefreshInterval();
});

// Initial setup of refresh interval
startRefreshInterval(); 